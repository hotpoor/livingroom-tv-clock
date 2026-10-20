import unittest
from trial_rules import explicit_facts, finalize


class TrialRulesTests(unittest.TestCase):
    def test_stocktake_and_months_are_not_item_quantity(self):
        text='有四包某品牌番茄沙司，在客厅冰箱上层，2026年6月13日的，保质期7个月'
        result=finalize(text, {'quantity':None, 'unit':None, 'action':'unknown','issues':['推算到期日期为2026-09-13']})
        self.assertEqual((result['quantity'],result['unit'],result['action']), (4,'包','stocktake'))
        self.assertEqual((result['startDate'],result['endDate']), ('2026-06-13','2027-01-13'))
        self.assertTrue(result['issues'])
        self.assertFalse(any('2026-09-13' in issue for issue in result['issues']))

    def test_month_end_and_leap_year(self):
        self.assertEqual(finalize('2024年1月31日的，保质期1个月', {})['endDate'],'2024-02-29')
        self.assertEqual(finalize('2024年2月29日的，保质期1年', {})['endDate'],'2025-02-28')

    def test_expiry_date_is_not_used_as_production_date(self):
        result=finalize('到期2027年1月13日，保质期7个月', {'endDate':'2027-01-13'})
        self.assertNotIn('startDate', result)
        self.assertEqual(result['endDate'],'2027-01-13')

    def test_invalid_date_and_ambiguous_or_fractional_count(self):
        self.assertIsNone(finalize('2026年2月30日的，保质期7个月', {})['endDate'])
        for text in ('有1.5包沙司', '有负四包沙司', '有四包沙司和两盒酸奶'):
            self.assertNotIn('quantity',explicit_facts(text))
        self.assertEqual(explicit_facts('吃掉三包沙司')['action'],'remove')


if __name__=='__main__':unittest.main()
