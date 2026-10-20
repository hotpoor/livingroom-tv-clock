"""LAN draft-only service. It never stores reminders or utterances."""
import concurrent.futures
import hmac
import json
import os
from pathlib import Path
import threading
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from schedule_parser import parse

CONFIG = json.loads(Path(os.environ.get('SCHEDULE_CONFIG', 'config.json')).read_text(encoding='utf-8'))
POOL = concurrent.futures.ThreadPoolExecutor(max_workers=1)
MODEL_LOCK = threading.Lock()


def extract_title(text, fallback):
    if not MODEL_LOCK.acquire(blocking=False):
        return fallback, False
    try:
        payload = {'model':'qwen3.5:0.8b','stream':False,'think':False,'keep_alive':'10m',
                   'format':{'type':'object','properties':{'title':{'type':'string'}},'required':['title']},
                   'messages':[{'role':'system','content':'提取用户要做的事项。删除日期、时间和“提醒我”。保留原文中文，不要翻译，不要添加内容。只输出JSON中的title。'},
                               {'role':'user','content':'明天上午十点提醒我交电费'}, {'role':'assistant','content':'{"title":"交电费"}'},
                               {'role':'user','content':'后天下午三点开会，下午两点出发'}, {'role':'assistant','content':'{"title":"开会"}'},
                               {'role':'user','content':text}],
                   'options':{'temperature':0,'num_ctx':2048,'num_predict':96}}
        req=urllib.request.Request(CONFIG.get('ollama','http://127.0.0.1:11435')+'/api/chat',data=json.dumps(payload).encode(),headers={'Content-Type':'application/json'})
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req,timeout=8) as response:
            title=json.loads(json.load(response)['message']['content'])['title'].strip()
        # A tiny model may hallucinate. Only accept exact source wording without time fragments.
        valid=bool(title and len(title)<=80 and fallback in (title,'去'+title,'要'+title))
        return (title if valid else fallback), valid
    except Exception:
        return fallback, False
    finally:
        MODEL_LOCK.release()


class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):
        pass  # No utterances/tokens in access logs.

    def respond(self,code,body):
        data=json.dumps(body,ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header('Content-Type','application/json; charset=utf-8')
        self.send_header('Content-Length',str(len(data)))
        self.send_header('Cache-Control','no-store')
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self.respond(200 if self.path=='/health' else 404,{'service':'schedule-drafts','status':'ok'})

    def do_POST(self):
        if self.path!='/parse':
            return self.respond(404,{'error':'not_found'})
        if not hmac.compare_digest(self.headers.get('Authorization',''), 'Bearer '+CONFIG['token']):
            return self.respond(401,{'error':'unauthorized'})
        self.connection.settimeout(10)
        try:
            length=int(self.headers.get('Content-Length','0'))
            if not 0<length<=8192:
                raise ValueError()
            body=json.loads(self.rfile.read(length))
            text=body['text']
            if not isinstance(text,str) or not 0<len(text.strip())<=500:
                raise ValueError()
        except (ValueError,KeyError,TypeError):
            return self.respond(400,{'error':'invalid_input'})
        draft=parse(text)
        # Only title extraction is delegated. Dates, times and flags stay deterministic.
        future=POOL.submit(extract_title,text,draft['title']) if not MODEL_LOCK.locked() else None
        try:
            title,used=future.result(timeout=9) if future else (draft['title'],False)
        except concurrent.futures.TimeoutError:
            title,used=draft['title'],False
        draft.update(title=title,modelUsed=used,requiresConfirmation=True)
        self.respond(200,draft)


if __name__=='__main__':
    if len(CONFIG.get('token',''))<24:
        raise SystemExit('Configure a strong LAN service token')
    ThreadingHTTPServer((CONFIG.get('bind','127.0.0.1'),CONFIG.get('port',18765)),Handler).serve_forever()
