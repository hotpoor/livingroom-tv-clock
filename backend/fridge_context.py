"""Bounded inventory hints for drafts; never an inventory write path."""
import difflib
import re
from schedule_parser import NUM, number
from fridge_parser import parse_fridge


def folded(text):
    """Normalize floor numbers and filler words, retaining source spans."""
    chars=[];spans=[];cursor=0
    for match in re.finditer(NUM+r'楼',text):
        for index in range(cursor,match.start()):chars.append(text[index]);spans.append((index,index+1))
        value=str(number(match[0][:-1]))+'楼'
        for char in value:chars.append(char);spans.append(match.span())
        cursor=match.end()
    for index in range(cursor,len(text)):chars.append(text[index]);spans.append((index,index+1))
    kept=[i for i,c in enumerate(chars) if not c.isspace() and c not in '的里，,。:：']
    return ''.join(chars[i] for i in kept),[spans[i] for i in kept]


def fridge_mentions(text,fridges):
    value,positions=folded(text)
    matches=[]
    for f in fridges:
        name=folded(f['name'])[0]
        if not name:continue
        for m in re.finditer(re.escape(name),value):matches.append((m.start(),m.end(),f))
    # Prefer the longest name when a shorter fridge name is contained in it.
    matches=[m for m in matches if not any(n[0]<=m[0] and n[1]>=m[1] and n[1]-n[0]>m[1]-m[0] for n in matches)]
    if not matches:
        for m in re.finditer(r'(?:\d+楼)?(?:客厅|厨房)?冰箱',value):
            candidates=[f for f in fridges if folded(f['name'])[0].endswith(m[0])]
            if len(candidates)==1:matches.append((m.start(),m.end(),candidates[0]))
    result=[]
    for begin,end,f in matches:
        if begin<len(positions) and end>begin:
            finish=positions[end-1][1];suffix=re.match(r'(?:里面的|里面|里边的|里边|里的|里|的)',text[finish:])
            start=positions[begin][0];prefix=re.search(r'(?:在|从|往|给|向)\s*$',text[:start])
            result.append((prefix.start() if prefix else start,finish+(len(suffix[0]) if suffix else 0),f))
    return result


def clean_context(raw):
    if not isinstance(raw,dict):return {'fridges':[],'preferredFridgeId':''}
    result={'preferredFridgeId':str(raw.get('preferredFridgeId',''))[:80],'fridges':[]}
    remaining=32
    for f in raw.get('fridges',[])[:20] if isinstance(raw.get('fridges'),list) else []:
        if not isinstance(f,dict):continue
        fridge={key:str(f.get(key,''))[:80] for key in ('id','name')}
        fridge['items']=[]
        for item in f.get('items',[]) if isinstance(f.get('items'),list) else []:
            if remaining<=0:break
            if not isinstance(item,dict):continue
            hint={key:str(item.get(key,''))[:80] for key in ('name','unit','location','endDate')}
            q=item.get('quantity')
            hint['quantity']=q if isinstance(q,int) and 0<=q<=999999 else None
            if not hint['name']:continue
            fridge['items'].append(hint);remaining-=1
        result['fridges'].append(fridge)
    return result


def location_suggestions(food,items):
    categories=('酸奶','牛奶','奶酪','饮料','罐头','烧卖','饺子','馄饨','包子','牛肉','猪肉','鸡肉','鱼丸','鸡蛋','苹果','草莓','蔬菜')
    scores={}
    for item in items:
        name=item['name'];location=item['location']
        if not food or not name or not location:continue
        if food==name:score=1.0
        elif min(len(food),len(name))>=2 and (food in name or name in food):score=.92
        else:
            score=difflib.SequenceMatcher(None,food,name).ratio()*.85
            if any(word in food and word in name for word in categories):score=max(score,.75)
        if score<.45:continue
        entry=scores.setdefault(location,{'location':location,'score':0,'evidence':[]})
        entry['score']=max(entry['score'],round(score*100))
        if name not in entry['evidence'] and len(entry['evidence'])<2:entry['evidence'].append(name)
    return sorted(scores.values(),key=lambda value:(-value['score'],value['location']))[:3]


def enrich(draft,text,context):
    fridges=context['fridges']
    mentions=fridge_mentions(text,fridges)
    mentioned={f['id']:f for _,_,f in mentions}
    if mentions:
        chars=list(text)
        for begin,end,_ in mentions:chars[begin:end]=' '*(end-begin)
        draft.update(parse_fridge(''.join(chars)))
    if len(mentioned)==1:
        target=next(iter(mentioned.values()));draft['fridgeName']=target['name'];draft['fridgeId']=target['id'];draft['fridgeMatched']=True
    elif len(mentioned)>1:
        draft['fridgeName']='多个冰箱';draft['unsupported']=True
        draft['issues'].append('包含多个冰箱，请一次处理一台冰箱')
    selected=[f for f in fridges if f['name']==draft['fridgeName']] if draft['fridgeName'] else [f for f in fridges if f['id']==context['preferredFridgeId']]
    if not selected and not draft['fridgeName']:selected=fridges
    items=[item for f in selected for item in f['items']]
    query=re.sub(r'\s','',draft['food'])
    names={item['name'] for item in items}
    exact=[name for name in names if re.sub(r'\s','',name)==query]
    candidates=exact or [name for name in names if len(query)>=2 and query in re.sub(r'\s','',name)]
    if not candidates and len(query)>=4:
        scores=sorted(((difflib.SequenceMatcher(None,query,name).ratio(),name) for name in names),reverse=True)
        if scores and scores[0][0]>=.8 and (len(scores)==1 or scores[0][0]-scores[1][0]>=.15):candidates=[scores[0][1]]
    draft['contextUsed']=bool(items)
    draft['foodCandidates']=sorted(candidates)
    if len(candidates)==1:
        full=candidates[0]
        if full!=draft['food']:
            draft['issues'].append('参考库存将「'+draft['food']+'」补全为「'+full+'」，请核对品名')
            draft['food']=full
        matching=[item for item in items if item['name']==full]
        locations={item['location'] for item in matching if item['location']}
        units={item['unit'] for item in matching if item['unit']}
        if len(selected)==1:
            if not draft['location'] and len(locations)==1:
                draft['location']=next(iter(locations));draft['locationInferred']=True
            if not draft['unit'] and len(units)==1:
                draft['unit']=next(iter(units));draft['unitInferred']=True
    elif len(candidates)>1:
        draft['issues'].append('同名或相近食材有多种，请选定完整品名和批次')
    draft['locationSuggestionsByFridge']={f['id']:location_suggestions(draft['food'],f['items']) for f in fridges}
    draft['locationSource']='same_food' if draft.get('locationInferred') else 'explicit' if draft['location'] else 'missing'
    return draft


def model_hint(context):
    return [{'冰箱':f['name'],'食材':[{'名称':i['name'],'余量':i['quantity'],'单位':i['unit'],'分区':i['location'],'到期':i['endDate']} for i in f['items']]} for f in context['fridges'] if f['items']]
