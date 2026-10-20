"""Bounded inventory hints for drafts; never an inventory write path."""
import difflib
import re


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


def enrich(draft,text,context):
    fridges=context['fridges']
    mentioned=[f for f in fridges if f['name'] and f['name'] in text]
    if len(mentioned)==1:draft['fridgeName']=mentioned[0]['name']
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
    return draft


def model_hint(context):
    return [{'冰箱':f['name'],'食材':[{'名称':i['name'],'余量':i['quantity'],'单位':i['unit'],'分区':i['location'],'到期':i['endDate']} for i in f['items']]} for f in context['fridges'] if f['items']]
