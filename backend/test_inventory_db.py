import copy
from pathlib import Path
import tempfile
import unittest
from inventory_db import InventoryDB,Conflict,validate_inventory,password_hash

DATA={'schemaVersion':1,'fridges':[{'id':'f1','name':'厨房冰箱','items':[{'id':'i1','name':'示例酸奶','quantity':5,'unit':'个','location':'上部冷藏','shelfLife':{'startDate':'2030-01-01','endDate':'2030-01-10'},'custom':'preserved'}]}]}


class InventoryTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.db=InventoryDB(Path(self.temp.name)/'inventory.sqlite3')
    def tearDown(self):self.temp.cleanup()
    def bootstrap(self):return self.db.sync('tv',-1,True,'initial',DATA)[1]
    def changed(self,amount):
        data=copy.deepcopy(DATA);data['fridges'][0]['items'][0]['quantity']=amount;return data
    def test_bootstrap_and_bidirectional(self):
        self.assertFalse(self.db.state()['initialized']);s=self.bootstrap();self.assertEqual(s['revision'],1)
        s=self.db.edit(1,self.changed(6),'web-a');self.assertEqual(s['revision'],2)
        code,s=self.db.sync('tv',1,False,'',DATA);self.assertEqual((code,s['inventory']['fridges'][0]['items'][0]['quantity']),(200,6))
        code,s=self.db.sync('tv',2,True,'tv-b',self.changed(4));self.assertEqual((code,s['revision'],s['acknowledgedChangeId']),(200,3,'tv-b'))
    def test_stale_web_edit_never_overwrites(self):
        self.bootstrap();self.db.edit(1,self.changed(6),'web-a')
        with self.assertRaises(Conflict):self.db.edit(1,self.changed(2),'web-b')
        self.assertEqual(self.db.state()['inventory'],self.changed(6))
    def test_offline_conflict_keep_server(self):
        self.bootstrap();self.db.edit(1,self.changed(7),'web-a');code,_=self.db.sync('tv',1,True,'offline',self.changed(2));self.assertEqual(code,409)
        state=self.db.state();self.assertEqual(state['inventory'],self.changed(7));self.assertEqual(state['conflicts'][0]['inventory'],self.changed(2))
        self.db.resolve('tv','offline',2,'server');code,state=self.db.sync('tv',1,True,'offline',self.changed(2))
        self.assertEqual((code,state['acknowledgedChangeId']),(200,'offline'));self.assertEqual(state['inventory'],self.changed(7));self.assertFalse(self.db.state()['conflicts'])
    def test_offline_conflict_keep_device_and_stale_resolution(self):
        self.bootstrap();self.db.edit(1,self.changed(7),'web-a');self.db.sync('tv',1,True,'offline',self.changed(2))
        with self.assertRaises(Conflict):self.db.resolve('tv','wrong-id',2,'device')
        state=self.db.resolve('tv','offline',2,'device');self.assertEqual(state['revision'],3);self.assertEqual(state['inventory'],self.changed(2))
    def test_lost_ack_is_idempotent_and_preserves_newer_web_edit(self):
        self.bootstrap();self.db.sync('tv',1,True,'tv-a',self.changed(4));self.db.edit(2,self.changed(8),'web-a')
        code,state=self.db.sync('tv',1,True,'tv-a',self.changed(4));self.assertEqual(state['revision'],3);self.assertEqual(state['inventory'],self.changed(8));self.assertEqual(state['acknowledgedChangeId'],'tv-a')
    def test_validation_and_extra_metadata(self):
        self.assertEqual(validate_inventory(DATA),DATA)
        for amount in (-1,1.5,True,1000000):
            with self.subTest(amount=amount),self.assertRaises(ValueError):validate_inventory(self.changed(amount))
        data=copy.deepcopy(DATA);data['fridges'][0]['items'][0]['shelfLife']['endDate']='2030-02-30'
        with self.assertRaises(ValueError):validate_inventory(data)
    def test_sessions_and_password_hash(self):
        token=self.db.session();self.assertTrue(self.db.authenticated(token));self.assertFalse(self.db.authenticated('bad'));self.db.logout(token);self.assertFalse(self.db.authenticated(token))
        hashed=password_hash('example-pass');self.assertEqual(password_hash('example-pass',hashed.split(':')[0]),hashed)
    def test_persistence(self):
        self.bootstrap();other=InventoryDB(self.db.path);self.assertEqual(other.state()['inventory'],DATA)


if __name__=='__main__':unittest.main()
