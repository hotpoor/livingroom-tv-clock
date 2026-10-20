package cn.livingroom.flipclock;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;

/** Fixtures are added and removed by unique ID; existing events are retained. */
public class SchedulePanelChecks extends Instrumentation {
    private final String prefix="schedule-check-"+UUID.randomUUID()+"-";
    private ClockActivity activity;
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private JSONObject task(int i,boolean past)throws Exception{return new JSONObject().put("id",prefix+(past?"past-":"future-")+i).put("title","日程联调 "+i).put("date",(past?"2020-01-":"2050-01-")+String.format(java.util.Locale.US,"%02d",i+1)).put("time","16:10").put("departureTime","").put("completed",false).put("createdAt",1000000L+i);}
    private SchedulePanel panel()throws Exception{return (SchedulePanel)field(activity,"schedule");}
    public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        try{
            JSONArray tasks=ScheduleStore.load(getTargetContext());for(int i=0;i<12;i++){tasks.put(task(i,true));tasks.put(task(i,false));}ScheduleStore.save(getTargetContext(),tasks);
            long created=tasks.getJSONObject(tasks.length()-1).getLong("createdAt");ScheduleStore.save(getTargetContext(),tasks);check(tasks.getJSONObject(tasks.length()-1).getLong("createdAt")==created,"Editing changes creation timestamp");
            JSONObject urgent=task(0,false).put("date",ScheduleStore.today()).put("time","00:00").put("mustFinishToday",true);check(!ScheduleStore.expired(urgent,ScheduleStore.at(ScheduleStore.today(),"12:00")),"Same-day deadline expired before end of day");
            activity=(ClockActivity)startActivitySync(new Intent(getTargetContext(),ClockActivity.class).putExtra("openPanel","schedule").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            runOnMainSync(()->{try{panel().focusTask(prefix+"past-0","start");}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(1100);waitForIdleSync();
            runOnMainSync(()->{try{SchedulePanel p=panel();check((Integer)field(p,"tab")==2,"Expired reminder selected wrong tab");View v=(View)((HashMap<?,?>)field(p,"cards")).get(prefix+"past-0");ScrollView scroll=(ScrollView)field(p,"scroll");check(v!=null&&v.hasFocus(),"Reminder focus missing");check(scroll.getScrollY()>0,"Reminder did not scroll");android.graphics.Rect rect=new android.graphics.Rect();v.getDrawingRect(rect);((LinearLayout)field(p,"body")).offsetDescendantRectToMyCoords(v,rect);check(rect.top>=scroll.getScrollY()&&rect.bottom<=scroll.getScrollY()+scroll.getHeight(),"Reminder card not fully visible");p.switchTab(-1);check((Integer)field(p,"tab")==1,"Recent tab switch failed");}catch(Exception e){throw new RuntimeException(e);}});
            runOnMainSync(()->{try{SchedulePanel p=panel();HashMap<?,?> cards=(HashMap<?,?>)field(p,"cards");LinearLayout body=(LinearLayout)field(p,"body");check(body.indexOfChild((View)cards.get(prefix+"past-11"))<body.indexOfChild((View)cards.get(prefix+"past-0")),"Creation ordering incorrect");p.switchTab(-1);cards=(HashMap<?,?>)field(p,"cards");check(body.indexOfChild((View)cards.get(prefix+"future-0"))<body.indexOfChild((View)cards.get(prefix+"future-11")),"Due ordering incorrect");}catch(Exception e){throw new RuntimeException(e);}});
            // Exercise the receiver and its foreground broadcast, targeting a low card.
            runOnMainSync(()->new ScheduleReceiver().onReceive(getTargetContext(),new Intent("cn.livingroom.flipclock.TASK_ALARM").setData(android.net.Uri.parse("livingroom://schedule/"+prefix+"past-1/start")).putExtra("taskId",prefix+"past-1").putExtra("phase","start").putExtra("title","日程联调").putExtra("message","测试定位")));
            SystemClock.sleep(1100);waitForIdleSync();runOnMainSync(()->{try{SchedulePanel p=panel();check((Integer)field(p,"tab")==2,"Alarm broadcast failed to select expired tab");check(((View)((HashMap<?,?>)field(p,"cards")).get(prefix+"past-1")).hasFocus(),"Alarm broadcast failed to focus event");}catch(Exception e){throw new RuntimeException(e);}});
            result.putString("stream","Schedule panel passed: deadline classification, creation timestamp, tabs, reminder focus and full-card scrolling.\n");code=Activity.RESULT_OK;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{
            try{JSONArray current=ScheduleStore.load(getTargetContext()),keep=new JSONArray(),remove=new JSONArray();NotificationManager notifications=(NotificationManager)getTargetContext().getSystemService(Context.NOTIFICATION_SERVICE);for(int i=0;i<current.length();i++){JSONObject t=current.getJSONObject(i);if(t.optString("id").startsWith(prefix)){t.put("completed",true);remove.put(t);notifications.cancel(("livingroom://schedule/"+t.optString("id")+"/start").hashCode());}else keep.put(t);}ScheduleStore.schedule(getTargetContext(),remove);ScheduleStore.save(getTargetContext(),keep);}catch(Exception ignored){}
            finish(code,result);
        }
    }
}
