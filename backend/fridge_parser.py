"""Draft-only food operations; quantities and direction never come from a model."""
import datetime as dt
import re
from schedule_parser import NUM, ZONE, number

ACTION = re.compile(r'放进(?:来|去)?(?:了)?|放入(?:了)?|放到(?:了)?|放了|存入(?:了)?|新增(?:了)?|增加(?:了)?|买了|补了|吃掉(?:了)?|吃了|喝掉(?:了)?|喝了|用掉(?:了)?|用完(?:了)?|拿走(?:了)?|取出(?:了)?|消耗(?:了)?|丢掉(?:了)?|扔了')
REMOVE = re.compile(r'吃|喝|用|拿走|取出|消耗|丢|扔')
QUANTITY = re.compile(r'('+NUM+r')(个|盒|瓶|袋|包|罐|根|颗|只|块|份|枚|杯|支|条)')
FRIDGE = re.compile(r'(?:[0-9一二三四五六七八九十]+楼)?(?:客厅|厨房)?冰箱')
LOCATION = re.compile(r'上(?:部|层)(?:冷藏)?|下(?:部|层)(?:冷藏)?|冷藏(?:室|区)?|冷冻(?:室|区)?|保鲜(?:室|区)?')
DATE = re.compile(r'(?<!\d)(\d{4})[-年/]?(\d{2})[-月/]?(\d{2})[日号]?(?!\d)')


def parse_fridge(text):
    text=text.strip()
    spans=[]
    issues=[]
    actions=list(ACTION.finditer(text))
    directions=set('remove' if REMOVE.search(m[0]) else 'add' for m in actions)
    action=next(iter(directions)) if len(directions)==1 else None
    if not action:
        issues.append('未听到明确动作，请选择放入还是吃掉')
    # Some TV IMEs omit the beginning of the utterance. Clean only this clear
    # orphaned particle; never infer whether the missing action was add/remove.
    orphan=re.match(r'^了\s*(?='+NUM+r'(?:个|盒|瓶|袋|包|罐|根|颗|只|块|份|枚|杯|支|条))',text)
    if orphan:spans.append(orphan.span())
    spans += [m.span() for m in actions]
    quantities=list(QUANTITY.finditer(text))
    quantity=number(quantities[0][1]) if len(quantities)==1 else None
    unit=quantities[0][2] if len(quantities)==1 else ''
    spans += [m.span() for m in quantities]
    if quantity is None or quantity<=0:
        issues.append('请填写本次变动的正整数数量')
    multiple=len(quantities)>1 or len(directions)>1 or bool(re.search(r'、|以及|还有',text))
    if multiple:
        issues.append('一次只处理一种食材和一个操作，请分开输入')
    fractional=bool(re.search(r'半(?:个|盒|瓶|袋|包|罐|斤)|\d+\.\d+|[0-9一二两三四五六七八九十]+(?:斤|公斤|千克|克)',text))
    negative=bool(re.search(r'(?:负|-)\s*'+NUM+r'(?:个|盒|瓶|袋|包|罐|根|颗|只|块|份|枚|杯|支|条)',text))
    if fractional or negative:
        quantity=None
        issues.append('目前按整件计数，请填写正整数数量和单位')
        spans += [m.span() for m in re.finditer(r'(?:\d+\.\d+|半|(?:负|-)\s*'+NUM+r')(?:个|盒|瓶|袋|包|罐|根|颗|只|块|份|枚|杯|支|条|公斤|千克|克|斤)',text)]
    def context_span(m):
        suffix=re.match(r'(?:里面的|里面|里边|的|里)',text[m.end():])
        return (m.start(),m.end()+(len(suffix[0]) if suffix else 0))
    fridges=list(FRIDGE.finditer(text));spans += [context_span(m) for m in fridges]
    fridge=fridges[0][0] if fridges else ''
    locations=list(LOCATION.finditer(text));spans += [context_span(m) for m in locations]
    def loc(m):
        return '上部冷藏' if m.startswith('上') else '下部冷藏' if m.startswith('下') else '冷冻' if m.startswith('冷冻') else '冷藏'
    location=loc(locations[0][0]) if locations else ''
    if len({m[0] for m in fridges})>1 or len({loc(m[0]) for m in locations})>1:
        multiple=True
        issues.append('包含不同冰箱或分区，请分开输入')
    dates=[]
    for m in DATE.finditer(text):
        spans.append(m.span())
        try:dates.append(dt.date(int(m[1]),int(m[2]),int(m[3])).isoformat())
        except ValueError:issues.append('保质期日期无效，请修改')
    if len(dates)>2:
        issues.append('保质期日期过多，请手动核对')
    start=dates[0] if len(dates)==2 else ''
    end=dates[-1] if dates else ''
    if start and start>end:issues.append('保质期开始日期晚于到期日期')
    if '保质期' in text and not dates and not re.search(r'无|未知|不知道|不清楚|没',text):
        issues.append('未识别保质期日期，可以在核对页填写')
    chars=list(text)
    for begin,end_span in spans:chars[begin:end_span]=' '*(end_span-begin)
    food=''.join(chars)
    food=re.sub(r'保质期(?:无|未知|不知道|不清楚)?|尽快食用|到期(?:日期)?|生产日期|有效期|我刚刚|我刚|刚刚|刚才|请记录|记录一下|帮忙|帮我|提示',' ',food)
    food=re.sub(r'^\s*(?:我|在|把|往)\s+',' ',food)
    food=re.sub(r'[\s，,。；;！!？?：:—\-~至]+',' ',food).strip()[:80]
    if re.search(r'.+和.+',food):
        multiple=True
        issues.append('一次只处理一种食材，请分开输入')
    if not food:issues.append('请填写食材名称')
    return {'food':food,'action':action,'quantity':quantity,'unit':unit,'fridgeName':fridge,'location':location,
            'startDate':start,'endDate':end,'issues':list(dict.fromkeys(issues)),
            'unsupported':multiple,'referenceTime':dt.datetime.now(ZONE).isoformat(timespec='seconds')}
