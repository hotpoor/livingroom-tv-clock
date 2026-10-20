import hmac
from http.cookies import SimpleCookie
import json
from pathlib import Path
import threading
import time
from urllib.parse import urlsplit
from inventory_socket import InventorySocket
from model_trial import trial
from inventory_db import InventoryDB,Conflict,password_hash


class WebRoutes:
    def __init__(self,config):
        self.config=config;self.root=Path(__file__).resolve().parent/'web'
        self.db=InventoryDB(config.get('inventory_db',str(self.root.parent/'state/inventory.sqlite3')))
        self.sockets=InventorySocket();self.attempts={};self.lock=threading.Lock()
    def cookie(self,h):
        try:
            cookie=SimpleCookie(h.headers.get('Cookie',''));return cookie['inventory_session'].value if 'inventory_session' in cookie else ''
        except Exception:return ''
    def bearer(self,h):return hmac.compare_digest(h.headers.get('Authorization',''),'Bearer '+self.config['token'])
    def authorized(self,h):return self.bearer(h) or self.db.authenticated(self.cookie(h))
    def send(self,h,status,body,headers=None):
        data=json.dumps(body,ensure_ascii=False).encode();h.send_response(status)
        for k,v in {'Content-Type':'application/json; charset=utf-8','Content-Length':str(len(data)),'Cache-Control':'no-store',**(headers or {})}.items():h.send_header(k,v)
        h.end_headers();h.wfile.write(data)
    def get(self,h):
        path=urlsplit(h.path).path
        if path=='/api/events':
            origin=h.headers.get('Origin','')
            if not self.authorized(h):self.send(h,401,{'error':'unauthorized'});return True
            if not self.bearer(h) and urlsplit(origin).netloc!=h.headers.get('Host',''):self.send(h,403,{'error':'origin'});return True
            self.sockets.serve(h);return True
        assets={'/':'index.html','/app.js':'app.js','/style.css':'style.css'}
        if path in assets:
            try:data=(self.root/assets[path]).read_bytes()
            except FileNotFoundError:self.send(h,503,{'error':'网页文件尚未部署'});return True
            h.send_response(200);h.send_header('Content-Type',{'/':'text/html; charset=utf-8','/app.js':'text/javascript; charset=utf-8','/style.css':'text/css; charset=utf-8'}[path]);h.send_header('Content-Length',str(len(data)));h.send_header('Cache-Control','no-store');h.send_header('X-Content-Type-Options','nosniff');h.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");h.end_headers();h.wfile.write(data);return True
        if path not in ('/api/inventory','/api/me'):return False
        if not self.authorized(h):self.send(h,401,{'error':'请先输入访问口令'});return True
        self.send(h,200,self.db.state() if path=='/api/inventory' else {'authenticated':True});return True
    def post(self,h):
        path=urlsplit(h.path).path
        if path not in ('/api/login','/api/logout','/api/inventory','/api/sync','/api/resolve','/api/model-trial'):return False
        try:
            if not self.bearer(h):
                origin=h.headers.get('Origin','')
                if urlsplit(origin).netloc!=h.headers.get('Host','') or h.headers.get('X-Requested-With')!='inventory-editor':
                    self.send(h,403,{'error':'请从本站页面操作'});return True
            if path!='/api/login' and not self.authorized(h):self.send(h,401,{'error':'请先输入访问口令'});return True
            length=int(h.headers.get('Content-Length','0'))
            if not 0<length<=1024*1024 or h.headers.get('Content-Type','').split(';')[0]!='application/json':raise ValueError('请求格式无效')
            h.connection.settimeout(15);body=json.loads(h.rfile.read(length))
            if not isinstance(body,dict):raise ValueError('请求格式无效')
            if path=='/api/model-trial':
                code,result=trial(self.config,self.db,body);self.send(h,code,result);return True
            if path=='/api/login':
                secret=self.config.get('web_password_hash','');password=body.get('password','')
                if not secret:self.send(h,503,{'error':'请先配置网页访问口令'});return True
                if not isinstance(password,str) or not 1<=len(password)<=120:raise ValueError('口令格式无效')
                with self.lock:
                    recent=[t for t in self.attempts.get(h.client_address[0],[]) if time.time()-t<60]
                    if len(recent)>=10:self.send(h,429,{'error':'尝试过多，请一分钟后再试'});return True
                    self.attempts[h.client_address[0]]=recent+[time.time()]
                if not hmac.compare_digest(password_hash(password,secret.split(':')[0]),secret):self.send(h,401,{'error':'口令不正确'});return True
                token=self.db.session();self.send(h,200,{'authenticated':True},{'Set-Cookie':'inventory_session='+token+'; HttpOnly; SameSite=Strict; Path=/; Max-Age=604800'});return True
            if path=='/api/logout':
                self.db.logout(self.cookie(h));self.send(h,200,{'ok':True},{'Set-Cookie':'inventory_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0'});return True
            if path=='/api/inventory':
                change=body.get('changeId');base=body.get('baseRevision')
                if not isinstance(base,int) or not isinstance(change,str) or not 1<=len(change)<=100:raise ValueError('缺少库存版本或修改标识')
                result=self.db.edit(base,body.get('inventory'),change);self.sockets.notify();self.send(h,200,result);return True
            if path=='/api/sync':
                if not self.bearer(h):self.send(h,403,{'error':'设备同步需要设备凭证'});return True
                code,result=self.db.sync(body.get('deviceId'),body.get('baseRevision'),body.get('dirty'),body.get('changeId',''),body.get('inventory'));self.send(h,code,result);
                if body.get('dirty'):self.sockets.notify()
                return True
            result=self.db.resolve(body.get('deviceId'),body.get('changeId'),body.get('baseRevision'),body.get('choice'));self.sockets.notify();self.send(h,200,result);return True
        except Conflict as error:self.send(h,409,{'error':str(error)});return True
        except (ValueError,TypeError,KeyError) as error:self.send(h,400,{'error':str(error) or '参数无效'});return True
