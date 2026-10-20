"""Deterministic Chinese date/time extraction. No model output enters these fields."""
import datetime as dt
import re

ZONE = dt.timezone(dt.timedelta(hours=8))
NUM = r'[0-9零〇一二两三四五六七八九十百]+'
PERIOD = r'(凌晨|早上|早晨|上午|中午|下午|晚上|傍晚|夜里)?'
CLOCK = re.compile(PERIOD + r'\s*(' + NUM + r')\s*(?:[:：](\d{1,2})|[点时](?:(半|一刻|三刻)|(' + NUM + r')分?)?)')
DAY = re.compile(r'(?:(\d{4})年)?(' + NUM + r')月(' + NUM + r')[日号]?')
ISO = re.compile(r'\d{4}-\d{2}-\d{2}')
WEEK = re.compile(r'(下下|下|本|这)?(?:周|星期|礼拜)([一二三四五六日天1234567])')
REL = re.compile(r'(半|'+NUM+r')(分钟|分|小时|钟头)(?:以)?后')


def number(value):
    if value.isdigit():
        return int(value)
    digits = dict(zip('零〇一二两三四五六七八九', [0, 0, 1, 2, 2, 3, 4, 5, 6, 7, 8, 9]))
    if '百' in value:
        left, right = value.split('百', 1)
        return digits.get(left, 1)*100 + (number(right.lstrip('零')) if right.lstrip('零') else 0)
    if '十' in value:
        left, right = value.split('十', 1)
        return digits.get(left, 1)*10 + (digits[right] if right else 0)
    return int(''.join(str(digits[c]) for c in value))


def parse(text, now=None):
    now = (now or dt.datetime.now(ZONE)).astimezone(ZONE)
    text = text.strip()
    issues, spans, dates, times = [], [], [], []
    relative = None
    for m in REL.finditer(text):
        amount = .5 if m[1] == '半' else number(m[1])
        relative = now + dt.timedelta(minutes=amount if m[2] in ('分钟', '分') else amount*60)
        spans.append(m.span())
    if len(list(REL.finditer(text))) > 1:
        issues.append('包含多个相对时间，请分别添加事项')
    for regex in (ISO, DAY):
        for m in regex.finditer(text):
            spans.append(m.span())
            try:
                dates.append(dt.date.fromisoformat(m[0]) if regex is ISO else dt.date(int(m[1] or now.year), number(m[2]), number(m[3])))
            except (ValueError, KeyError):
                issues.append('日期无效，请修改')
    for m in re.finditer(r'大后天|后天|明天|明日|今天|今日', text):
        spans.append(m.span())
        dates.append(now.date()+dt.timedelta(days={'今天':0,'今日':0,'明天':1,'明日':1,'后天':2,'大后天':3}[m[0]]))
    for m in WEEK.finditer(text):
        spans.append(m.span())
        day = '一二三四五六日'.find(m[2].replace('天','日')) if not m[2].isdigit() else int(m[2])-1
        if m[1]:
            offset = {'下下':2,'下':1,'本':0,'这':0}[m[1]]*7 + day-now.weekday()
        else:
            offset = (day-now.weekday()) % 7
        dates.append(now.date()+dt.timedelta(days=offset))
    if len(set(dates)) > 1:
        issues.append('包含不同日期，请核对并分别添加')
    for m in CLOCK.finditer(text):
        spans.append(m.span())
        try:
            period, hour = m[1], number(m[2])
            minute = int(m[3]) if m[3] else {'半':30,'一刻':15,'三刻':45}.get(m[4],number(m[5]) if m[5] else 0)
            if period in ('下午','晚上','傍晚','夜里') and 1 <= hour < 12:
                hour += 12
            elif period == '中午' and 1 <= hour < 11:
                hour += 12
            elif period in ('凌晨','早上','早晨','上午') and hour == 12:
                hour = 0
            if not 0 <= hour <= 23 or not 0 <= minute <= 59:
                raise ValueError()
            value = f'{hour:02d}:{minute:02d}'
            after = text[m.end():m.end()+7]
            before = text[max(0,m.start()-5):m.start()]
            departure = bool(re.match(r'\s*(?:就|要)?出发',after) or re.search(r'出发(?:时间)?[是为：:\s]*$',before))
            times.append((value, departure))
        except (ValueError, KeyError):
            issues.append('时间无效，请修改')
    starts = [v for v, departure in times if not departure]
    departures = [v for v, departure in times if departure]
    if len(starts)>1 or len(departures)>1 or (relative and (dates or times)):
        issues.append('包含多个时间，请核对计划时间和出发时间')
    # Unsupported date language is never silently converted to today.
    unsupported = bool(re.search(r'明年|今年|下个?月|月底|月初|每天|每周|每星期|周末|下周(?![一二三四五六日天1-7])|下星期(?![一二三四五六日天1-7])|\d+天后|[一二三四五六七八九十]+天后|早饭|午饭|晚饭',text))
    if unsupported:
        issues.append('这类日期或重复规则暂不支持，请填写具体日期时间')
    date = relative.date().isoformat() if relative else (dates[0].isoformat() if dates else now.date().isoformat() if times and not unsupported and not any('日期无效' in i for i in issues) else None)
    start = relative.strftime('%H:%M') if relative else starts[0] if starts else None
    depart = departures[0] if departures else None
    if not date:
        issues.append('请补充日期')
    if not start:
        issues.append('请补充具体计划时间')
    if date and start:
        target = dt.datetime.fromisoformat(date+'T'+start).replace(tzinfo=ZONE)
        if target <= now:
            issues.append('计划时间已经过去，请核对日期和时间')
    if depart and start and depart>start:
        issues.append('出发时间晚于计划时间，请核对')
    # Preserve source wording for a deterministic fallback title.
    chars = list(text)
    for begin,end in spans:
        chars[begin:end] = ' '*(end-begin)
    candidate = ''.join(chars)
    candidate = re.sub(r'(?:今天|今日)?(?:一定|必须|务必)要?完成|提醒我|帮我记住|帮我记下|帮我记录|帮我|请提醒|提醒|开始|出发时间|出发|早上|早晨|上午|中午|下午|晚上|傍晚|凌晨',' ',candidate)
    candidate = re.sub(r'[\s，,。；;！!？?：:]+',' ',candidate).strip()[:80]
    if not candidate:
        issues.append('请填写事项内容')
    return {'title':candidate,'date':date,'time':start,'departureTime':depart,
            'mustFinishToday':bool(re.search(r'(?:一定|必须|务必)要?完成',text)),
            'issues':list(dict.fromkeys(issues)), 'referenceTime':now.isoformat(timespec='seconds')}
