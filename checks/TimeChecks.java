package cn.livingroom.flipclock;
public class TimeChecks {
    public static void main(String[] args){
        if(ScheduleStore.at("2026-10-20","16:00")!=1792483200000L)throw new AssertionError("Shanghai departure time");
        if(ScheduleStore.at("2026-02-30","16:00")!=-1)throw new AssertionError("Invalid date accepted");
        if(ScheduleStore.at("2026-10-20","25:00")!=-1)throw new AssertionError("Invalid hour accepted");
        if(!ScheduleStore.countdown(-1000).equals("00分 00秒"))throw new AssertionError("Negative countdown");
        if(!ScheduleStore.countdown(1).equals("00分 01秒"))throw new AssertionError("Rounding before deadline");
        if(!ScheduleStore.countdown(3661000).equals("1小时 01分 01秒"))throw new AssertionError("Hour boundary");
        System.out.println("6 time and countdown checks passed");
    }
}
