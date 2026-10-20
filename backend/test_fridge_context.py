import unittest
from fridge_context import clean_context,enrich
from fridge_parser import parse_fridge

RAW={'preferredFridgeId':'living','fridges':[{'id':'living','name':'客厅冰箱','items':[{'name':'红颜草莓酸奶','unit':'个','location':'上部冷藏','endDate':'2026-11-10'}]},{'id':'kitchen','name':'厨房冰箱','items':[{'name':'另一款草莓酸奶','unit':'瓶','location':'冷藏'}]}]}


class ContextTests(unittest.TestCase):
    def draft(self,text,raw=RAW):return enrich(parse_fridge(text),text,clean_context(raw))
    def test_alias_and_fields(self):
        r=self.draft('放进了两盒草莓酸奶')
        self.assertEqual((r['food'],r['quantity'],r['unit'],r['location']),('红颜草莓酸奶',2,'盒','上部冷藏'))
        self.assertEqual(r['endDate'],'') # New batch never inherits old expiry.
        self.assertTrue(r['issues'])
    def test_missing_action_stays_missing(self):
        r=self.draft('了两盒草莓酸奶')
        self.assertIsNone(r['action'])
        self.assertEqual(r['food'],'红颜草莓酸奶')
    def test_explicit_fridge(self):
        r=self.draft('厨房冰箱放入两个草莓酸奶')
        self.assertEqual((r['food'],r['fridgeName']),('另一款草莓酸奶','厨房冰箱'))
    def test_ambiguous_and_new_food(self):
        self.assertEqual(self.draft('放进苹果两个')['food'],'苹果')
        raw=dict(RAW,preferredFridgeId='')
        r=self.draft('放进草莓酸奶两个',raw)
        self.assertEqual(r['food'],'草莓酸奶')
        self.assertEqual(len(r['foodCandidates']),2)
    def test_bounds(self):
        raw={'fridges':[{'items':[{'name':'测试'}]*1000}]*50}
        c=clean_context(raw)
        self.assertEqual(len(c['fridges']),20)
        self.assertEqual(sum(len(f['items']) for f in c['fridges']),32)


if __name__=='__main__':unittest.main()
