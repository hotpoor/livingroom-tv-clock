"""Read-only browser model trial. Never writes inventory or reminders."""
import datetime
import json
import threading
import time
import urllib.request
from zoneinfo import ZoneInfo
from trial_rules import explicit_facts, finalize

LOCK = threading.Lock()


def trial(config, db, body):
    text, mode = body.get('text'), body.get('mode', 'fridge')
    if not isinstance(text, str) or not 1 <= len(text.strip()) <= 1000 or mode not in ('fridge', 'schedule'):
        raise ValueError('请输入1000字以内的内容，并选择测试类型')
    if not LOCK.acquire(blocking=False):
        return 429, {'error': '4B 正在处理另一条测试，请稍后再试'}
    try:
        now = datetime.datetime.now(ZoneInfo('Asia/Shanghai')).isoformat(timespec='seconds')
        if mode == 'fridge':
            inventory = db.state().get('inventory') or {}
            context = []
            for fridge in inventory.get('fridges', [])[:20]:
                context.append({'id': fridge['id'], 'name': fridge['name'], 'items': [
                    {k: item.get(k) for k in ('id', 'name', 'unit', 'location', 'quantity')}
                    for item in fridge.get('items', [])[:20]]})
            system = ('你负责提取冰箱操作候选，不执行操作。仅输出JSON对象，字段：food、quantity(整数或null)、unit、action(add/remove/stocktake/unknown)、fridgeId、fridgeName、location、startDate、endDate、issues(字符串数组)。'
                      '原句是事实来源，库存只是辅助参考。新食材无需在库存存在。必须保留原句明确的数量、单位、品牌完整品名、日期，不可因库存不存在而置空。不得在issues编造库存内容或声称未匹配就无法提取。'
                      '有四包表示quantity=4,unit=包,action=stocktake（现有库存陈述），不是新增。放入才是add，吃掉才是remove。品牌如肯德基须保留。'
                      '用户明确的冰箱和区域优先；上层匹配上部冷藏，下层匹配下部冷藏；不确定区域不猜，库存推断须注明。单独一个日期加保质期时仅提取startDate，endDate留空，程序负责计算到期日，模型不得在issues推算或评论到期日期。未提供的字段留空，不编造日期或过期状态。遇到多种食材在issues要求分开输入。'
                      '程序识别的原句事实（必须保留）：'+json.dumps(explicit_facts(text), ensure_ascii=False)+'。'
                      '当前上海时间：'+now+'。现有库存：'+json.dumps(context, ensure_ascii=False, separators=(',', ':')))
        else:
            system = ('你负责提取日程候选，不保存或创建提醒。仅输出JSON对象，字段：title、date(YYYY-MM-DD)、time(HH:mm)、departureTime(HH:mm)、mustFinishToday(布尔)、issues(字符串数组)。'
                      '分清事件时间与出发时间，不确定的时间留空并在issues注明。当前上海时间：'+now)
        payload = {'model': 'qwen3.5:4b', 'stream': False, 'think': False, 'format': 'json', 'keep_alive': '5m',
                   'messages': [{'role': 'system', 'content': system}, {'role': 'user', 'content': text.strip()}],
                   'options': {'temperature': 0, 'num_ctx': 8192, 'num_predict': 512}}
        request = urllib.request.Request(config.get('ollama', 'http://127.0.0.1:11435')+'/api/chat',
                                         data=json.dumps(payload).encode(), headers={'Content-Type': 'application/json'})
        started = time.monotonic()
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request, timeout=90) as response:
            result = json.load(response)
        if not result.get('done') or result.get('done_reason') == 'length':
            return 502, {'error': '模型输出不完整，请缩短输入再试'}
        draft = json.loads(result['message']['content'])
        if not isinstance(draft, dict):
            return 502, {'error': '模型未返回有效对象，请重试'}
        if mode == 'fridge':
            draft = finalize(text, draft)
        return 200, {'model': 'qwen3.5:4b', 'seconds': round(time.monotonic()-started, 2),
                     'draft': draft, 'readOnly': True, 'mode': mode}
    except (OSError, ValueError, KeyError):
        return 502, {'error': '4B 暂时无法完成解析，请稍后重试'}
    finally:
        LOCK.release()
