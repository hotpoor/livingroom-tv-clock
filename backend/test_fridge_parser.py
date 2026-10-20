import unittest
from fridge_parser import parse_fridge


class FridgeTests(unittest.TestCase):
    def test_single_food(self):
        cases=[
            ('客厅冰箱放进两盒红颜草莓酸奶','add',2,'盒','红颜草莓酸奶','客厅冰箱',''),
            ('冷冻的牛肉烧卖吃掉了三个','remove',3,'个','牛肉烧卖','','冷冻'),
            ('放进黄桃罐头1罐，保质期无','add',1,'罐','黄桃罐头','',''),
            ('厨房冰箱里放入了五瓶牛奶','add',5,'瓶','牛奶','厨房冰箱',''),
            ('我吃了蜜桃酸奶两个','remove',2,'个','蜜桃酸奶','',''),
            ('在冷冻里放进了六个牛肉烧卖','add',6,'个','牛肉烧卖','','冷冻'),
            ('放进了里脊肉三块','add',3,'块','里脊肉','',''),
            ('红颜草莓酸奶喝掉了十瓶','remove',10,'瓶','红颜草莓酸奶','',''),
            ('放进去了两盒红颜草莓酸奶','add',2,'盒','红颜草莓酸奶','',''),
            ('蓝莓酸奶放入两个，在冷藏上层','add',2,'个','蓝莓酸奶','','上部冷藏'),
        ]
        for text,*expected in cases:
            with self.subTest(text=text):
                result=parse_fridge(text)
                self.assertEqual([result[k] for k in ('action','quantity','unit','food','fridgeName','location')],expected)
                self.assertEqual(result['issues'],[])

    def test_life(self):
        r=parse_fridge('上部冷藏放进红颜草莓酸奶五盒，保质期20300101-20300110')
        self.assertEqual((r['food'],r['startDate'],r['endDate']),('红颜草莓酸奶','2030-01-01','2030-01-10'))
        self.assertTrue(any('无效' in i for i in parse_fridge('放进牛奶一瓶，保质期20260230')['issues']))

    def test_ambiguity(self):
        partial=parse_fridge('了两盒红颜草莓酸奶')
        self.assertEqual((partial['food'],partial['quantity']),('红颜草莓酸奶',2))
        self.assertIsNone(partial['action'])
        self.assertIsNone(parse_fridge('吃掉了几个酸奶')['quantity'])
        self.assertIsNone(parse_fridge('酸奶两个')['action'])
        self.assertTrue(parse_fridge('放进苹果两个和梨三个')['unsupported'])
        self.assertTrue(parse_fridge('放进苹果两个，吃掉苹果一个')['unsupported'])
        self.assertIsNone(parse_fridge('吃掉酸奶半盒')['quantity'])
        self.assertIsNone(parse_fridge('吃掉酸奶1.5盒')['quantity'])
        self.assertIsNone(parse_fridge('放进牛肉两斤')['quantity'])
        self.assertIsNone(parse_fridge('放进酸奶-2盒')['quantity'])


if __name__=='__main__':unittest.main()
