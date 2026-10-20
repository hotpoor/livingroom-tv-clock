import datetime as dt
import unittest
from schedule_parser import ZONE, parse

NOW=dt.datetime(2026,10,20,15,30,tzinfo=ZONE)


class DateTimeTests(unittest.TestCase):
    def test_supported_phrases(self):
        cases=[
            ('明天下午四点提醒我买牛奶','2026-10-21','16:00',None,'买牛奶'),
            ('今天下午五点开始整理账目，今天一定要完成','2026-10-20','17:00',None,'整理账目'),
            ('明天下午四点十分接孩子，下午四点出发','2026-10-21','16:10','16:00','接孩子'),
            ('后天早上八点半去跑步','2026-10-22','08:30',None,'去跑步'),
            ('下周三上午九点开会','2026-10-28','09:00',None,'开会'),
            ('半小时后提醒我喝水','2026-10-20','16:00',None,'喝水'),
            ('16点10分幼儿园接小朋友放学，16:00出发','2026-10-20','16:10','16:00','幼儿园接小朋友放学'),
            ('星期五下午三点看书','2026-10-23','15:00',None,'看书'),
            ('下下周一上午十点开会','2026-11-02','10:00',None,'开会'),
            ('明日中午十二点吃饭','2026-10-21','12:00',None,'吃饭'),
            ('明天晚上八点一刻看电影','2026-10-21','20:15',None,'看电影'),
            ('明天凌晨十二点半吃药','2026-10-21','00:30',None,'吃药'),
            ('两小时后喝水','2026-10-20','17:30',None,'喝水'),
            ('四十分钟后出门','2026-10-20','16:10',None,'出门'),
            ('2026年11月2日早上八点出门','2026-11-02','08:00',None,'出门'),
            ('十一月二日晚上九点散步','2026-11-02','21:00',None,'散步'),
            ('2026-11-02 18:05 吃饭','2026-11-02','18:05',None,'吃饭'),
            ('明天16：10接孩子，出发时间16：00','2026-10-21','16:10','16:00','接孩子'),
        ]
        for text,date,time,departure,title in cases:
            with self.subTest(text=text):
                result=parse(text,NOW)
                self.assertEqual([result[k] for k in ('date','time','departureTime','title')],[date,time,departure,title])
                self.assertEqual(result['issues'],[])

    def test_missing_and_unsafe_inputs(self):
        cases=[
            ('周五下午开会','具体计划时间'),
            ('帮我记住买洗衣液','请补充日期'),
            ('明天25:00开会','时间无效'),
            ('明天16:90开会','时间无效'),
            ('2026年2月30日开会','日期无效'),
            ('明天九点开会，后天十点吃饭','不同日期'),
            ('明天九点开会，十点吃饭','多个时间'),
            ('明天十点开会，十一点出发','出发时间晚于'),
            ('下个月三号下午四点缴费','暂不支持'),
            ('每天下午四点喝水','暂不支持'),
            ('周末下午三点爬山','暂不支持'),
            ('三天后下午三点爬山','暂不支持'),
            ('今天上午八点跑步','已经过去'),
            ('半小时后，明天再提醒我喝水','多个时间'),
        ]
        for text,issue in cases:
            with self.subTest(text=text):
                self.assertTrue(any(issue in i for i in parse(text,NOW)['issues']))
        self.assertIsNone(parse('周五下午开会',NOW)['time'])
        self.assertIsNone(parse('下个月三号下午四点缴费',NOW)['date'])

    def test_boundary_and_flags(self):
        result=parse('半小时后提醒我吃药',dt.datetime(2026,12,31,23,45,tzinfo=ZONE))
        self.assertEqual((result['date'],result['time']),('2027-01-01','00:15'))
        self.assertTrue(parse('今天17:00报税，今天一定要完成',NOW)['mustFinishToday'])
        self.assertFalse(parse('明天17:00报税',NOW)['mustFinishToday'])


if __name__=='__main__':
    unittest.main()
