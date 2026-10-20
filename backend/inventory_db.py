"""Versioned shared inventory, durable conflict drafts, and browser sessions."""
import copy
from contextlib import contextmanager
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import secrets
import sqlite3
import threading
import time


class Conflict(Exception):pass


def validate_inventory(data):
    if not isinstance(data,dict) or not isinstance(data.get('fridges'),list) or len(data['fridges'])>20:
        raise ValueError('库存格式无效，最多支持20台冰箱')
    ids=set();names=set()
    def string(value,limit,label):
        if not isinstance(value,str) or not value.strip() or len(value)>limit:raise ValueError(label+'无效')
    for fridge in data['fridges']:
        if not isinstance(fridge,dict):raise ValueError('冰箱格式无效')
        string(fridge.get('id'),80,'冰箱ID');string(fridge.get('name'),20,'冰箱名称')
        if fridge['id'] in ids or fridge['name'].strip() in names:raise ValueError('冰箱ID或名称重复')
        ids.add(fridge['id']);names.add(fridge['name'].strip());seen=set()
        if not isinstance(fridge.get('items'),list) or len(fridge['items'])>500:raise ValueError('食材清单无效')
        for item in fridge['items']:
            if not isinstance(item,dict):raise ValueError('食材格式无效')
            for field,limit,label in (('id',80,'批次ID'),('name',80,'食材名称'),('unit',8,'单位'),('location',40,'分区')):string(item.get(field),limit,label)
            if item['id'] in seen:raise ValueError('批次ID重复')
            seen.add(item['id']);q=item.get('quantity')
            if isinstance(q,bool) or not isinstance(q,int) or not 0<=q<=999999:raise ValueError('数量应为0到999999的整数')
            life=item.get('shelfLife')
            if life is not None:
                if not isinstance(life,dict):raise ValueError('保质期格式无效')
                start,end=life.get('startDate',''),life.get('endDate','')
                try:
                    if not isinstance(start,str) or not isinstance(end,str):raise ValueError()
                    if start and dt.date.fromisoformat(start).isoformat()!=start:raise ValueError()
                    if dt.date.fromisoformat(end).isoformat()!=end or start and start>end:raise ValueError()
                except ValueError:raise ValueError('保质期日期无效或顺序错误')
            if not isinstance(item.get('reminder',''),str) or len(item.get('reminder',''))>120:raise ValueError('备注过长')
    return copy.deepcopy(data)


def password_hash(password,salt=None):
    salt=salt or secrets.token_hex(16)
    digest=hashlib.pbkdf2_hmac('sha256',password.encode(),bytes.fromhex(salt),210000).hex()
    return salt+':'+digest


class InventoryDB:
    def __init__(self,path):
        self.path=Path(path);self.path.parent.mkdir(mode=0o700,parents=True,exist_ok=True);self.lock=threading.RLock()
        with self.connection() as db:
            db.executescript('''
              CREATE TABLE IF NOT EXISTS state(id INTEGER PRIMARY KEY CHECK(id=1),revision INTEGER,data TEXT,updated REAL);
              CREATE TABLE IF NOT EXISTS history(revision INTEGER PRIMARY KEY,data TEXT,source TEXT,updated REAL);
              CREATE TABLE IF NOT EXISTS changes(id TEXT PRIMARY KEY,revision INTEGER);
              CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY,seen REAL,applied INTEGER,pending_id TEXT,pending_data TEXT,pending_base INTEGER,resolved_id TEXT);
              CREATE TABLE IF NOT EXISTS resolved_backups(id INTEGER PRIMARY KEY,data TEXT,device TEXT,updated REAL);
              CREATE TABLE IF NOT EXISTS sessions(digest TEXT PRIMARY KEY,expires REAL);
            ''')
        try:self.path.chmod(0o600)
        except OSError:pass
    @contextmanager
    def connection(self):
        db=sqlite3.connect(self.path,timeout=10);db.row_factory=sqlite3.Row
        try:
            with db:yield db
        finally:db.close()
    def _state(self,db):
        row=db.execute('SELECT * FROM state WHERE id=1').fetchone()
        return {'initialized':bool(row),'revision':row['revision'] if row else 0,'inventory':json.loads(row['data']) if row else None,'updatedAt':row['updated'] if row else None}
    def state(self):
        with self.lock,self.connection() as db:
            result=self._state(db)
            result['devices']=[{'id':r['id'],'lastSeen':r['seen'],'revision':r['applied']} for r in db.execute('SELECT * FROM devices')]
            result['conflicts']=[{'deviceId':r['id'],'changeId':r['pending_id'],'baseRevision':r['pending_base'],'inventory':json.loads(r['pending_data'])} for r in db.execute('SELECT * FROM devices WHERE pending_id IS NOT NULL')]
            return result
    def _write(self,db,data,source,change_id):
        state=self._state(db);version=state['revision']+1;now=time.time();raw=json.dumps(data,ensure_ascii=False,separators=(',',':'))
        db.execute('INSERT OR REPLACE INTO state VALUES(1,?,?,?)',(version,raw,now))
        db.execute('INSERT INTO history VALUES(?,?,?,?)',(version,raw,source,now))
        db.execute('INSERT OR REPLACE INTO changes VALUES(?,?)',(change_id,version))
        db.execute('DELETE FROM history WHERE revision<?',(version-49,))
        return self._state(db)
    def edit(self,base,data,change_id):
        data=validate_inventory(data)
        with self.lock,self.connection() as db:
            db.execute('BEGIN IMMEDIATE');state=self._state(db)
            if db.execute('SELECT 1 FROM changes WHERE id=?',(change_id,)).fetchone():return state
            if not state['initialized']:raise Conflict('等待电视首次同步库存')
            if base!=state['revision']:raise Conflict('库存已被其他设备修改，本次未保存，请重新核对')
            return self._write(db,data,'web',change_id)
    def sync(self,device,base,dirty,change_id,data):
        if not isinstance(device,str) or not 1<=len(device)<=80 or not isinstance(base,int) or not isinstance(dirty,bool) or not isinstance(change_id,str) or len(change_id)>100:raise ValueError('同步参数无效')
        data=validate_inventory(data)
        with self.lock,self.connection() as db:
            db.execute('BEGIN IMMEDIATE');state=self._state(db)
            db.execute('INSERT INTO devices(id,seen,applied) VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET seen=excluded.seen,applied=excluded.applied',(device,time.time(),base))
            known=db.execute('SELECT * FROM devices WHERE id=?',(device,)).fetchone()
            acknowledged=bool(change_id and (known['resolved_id']==change_id or db.execute('SELECT 1 FROM changes WHERE id=?',(change_id,)).fetchone()))
            if not state['initialized']:
                result=self._write(db,data,'tv-initial',change_id or secrets.token_hex(16));result['acknowledgedChangeId']=change_id;return 200,result
            if acknowledged:
                state['acknowledgedChangeId']=change_id;return 200,state
            if dirty:
                if not change_id:raise ValueError('缺少修改标识')
                if base==state['revision']:
                    result=self._write(db,data,'tv',change_id)
                    db.execute('UPDATE devices SET pending_id=NULL,pending_data=NULL,pending_base=NULL WHERE id=?',(device,))
                    result['acknowledgedChangeId']=change_id;return 200,result
                db.execute('UPDATE devices SET pending_id=?,pending_data=?,pending_base=? WHERE id=?',(change_id,json.dumps(data,ensure_ascii=False),base,device))
                state['error']='conflict';return 409,state
            return 200,state
    def resolve(self,device,change_id,base,choice):
        if choice not in ('device','server'):raise ValueError('请选择保留哪份库存')
        with self.lock,self.connection() as db:
            db.execute('BEGIN IMMEDIATE');state=self._state(db)
            row=db.execute('SELECT * FROM devices WHERE id=?',(device,)).fetchone()
            if base!=state['revision'] or not row or row['pending_id']!=change_id:raise Conflict('库存或待处理修改已变化，请重新核对')
            db.execute('INSERT INTO resolved_backups(data,device,updated) VALUES(?,?,?)',(row['pending_data'],device,time.time()))
            db.execute('DELETE FROM resolved_backups WHERE id NOT IN (SELECT id FROM resolved_backups ORDER BY id DESC LIMIT 30)')
            if choice=='device':state=self._write(db,json.loads(row['pending_data']),'resolved-tv',secrets.token_hex(16))
            db.execute('UPDATE devices SET resolved_id=?,pending_id=NULL,pending_data=NULL,pending_base=NULL WHERE id=?',(change_id,device))
            return state
    def session(self):
        token=secrets.token_urlsafe(32)
        with self.lock,self.connection() as db:
            db.execute('DELETE FROM sessions WHERE expires<?',(time.time(),))
            db.execute('INSERT INTO sessions VALUES(?,?)',(hashlib.sha256(token.encode()).hexdigest(),time.time()+7*86400))
        return token
    def authenticated(self,token):
        with self.lock,self.connection() as db:return bool(db.execute('SELECT 1 FROM sessions WHERE digest=? AND expires>?',(hashlib.sha256(token.encode()).hexdigest(),time.time())).fetchone())
    def logout(self,token):
        with self.lock,self.connection() as db:db.execute('DELETE FROM sessions WHERE digest=?',(hashlib.sha256(token.encode()).hexdigest(),))
