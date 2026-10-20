"""Preserve explicit quantities and compute shelf life for read-only model trials."""
import calendar
import datetime
import re
from fridge_parser import QUANTITY, ACTION, REMOVE
from schedule_parser import NUM, number


def explicit_facts(text):
    facts = {}
    quantities = [m for m in QUANTITY.finditer(text) if text[m.end():m.end()+1] != '月']
    if len(quantities) == 1:
        m = quantities[0]
        if not re.search(r'[.负\-]\s*$', text[:m.start()]):
            facts.update(quantity=number(m[1]), unit=m[2])
    actions = {'remove' if REMOVE.search(m[0]) else 'add' for m in ACTION.finditer(text)}
    if len(actions) == 1:
        facts['action'] = next(iter(actions))
    elif not actions and re.match(r'^\s*(?:现在|目前|现有)?\s*(?:有|还剩|剩下)', text):
        facts['action'] = 'stocktake'
    return facts


def finalize(text, draft):
    draft.update(explicit_facts(text))
    issues = draft.get('issues')
    issues = issues if isinstance(issues, list) else []
    if draft.get('action') == 'stocktake':
        issues.append('这是现有数量陈述，不是新增或扣减；实际保存前需确认操作方式')
    dates = list(re.finditer(r'(?<!\d)(\d{4})[-年/](\d{1,2})[-月/](\d{1,2})[日号]?(?!\d)', text))
    duration = re.search(r'保质期\s*(?:为|是)?\s*('+NUM+r')\s*(?:个)?(月|年|天|日)', text)
    if len(dates) == 1 and duration:
        # Date arithmetic and its explanation must have one authoritative source.
        issues = [s for s in issues if not re.search(r'日期|到期|保质期|有效|生产|过期|\d{4}|推算', str(s))]
        try:
            start = datetime.date(*(int(dates[0][i]) for i in (1, 2, 3)))
            amount = number(duration[1])
            if not 0 < amount <= 1200:
                raise ValueError()
            if duration[2] in ('月', '年'):
                months = amount * (12 if duration[2] == '年' else 1)
                ordinal = start.year*12+start.month-1+months
                year, month = ordinal//12, ordinal % 12+1
                end = datetime.date(year, month, min(start.day, calendar.monthrange(year, month)[1]))
            else:
                end = start+datetime.timedelta(days=amount)
            if re.search(r'到期|截止|有效至', text[max(0, dates[0].start()-8):dates[0].end()+2]):
                issues.append('日期可能是到期日期，未按生产日期推算，请核对包装')
            else:
                draft.update(startDate=start.isoformat(), endDate=end.isoformat())
                issues.append('按该日期为生产／开始日期、按日历时长推算到期日；请核对包装标注')
        except (ValueError, OverflowError):
            draft.update(startDate=None, endDate=None)
            issues.append('日期或保质期无效，请核对')
    draft['issues'] = list(dict.fromkeys(str(s) for s in issues))
    return draft
