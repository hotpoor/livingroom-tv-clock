package cn.livingroom.flipclock;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class ScheduleStore {
    static final TimeZone ZONE=TimeZone.getTimeZone("Asia/Shanghai");
    static JSONArray load(Context c){
        try {
            String stored=c.getSharedPreferences("schedule",0).getString("tasks",null);
            if(stored!=null){JSONArray tasks=new JSONArray(stored);if(ensureCreated(tasks))c.getSharedPreferences("schedule",0).edit().putString("tasks",tasks.toString()).commit();return tasks;}
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=c.getAssets().open("schedule.json")){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            JSONArray tasks=new JSONObject(out.toString("UTF-8")).getJSONArray("tasks");save(c,tasks);return tasks;
        }catch(Exception e){android.util.Log.e("FlipClock","Schedule load failed",e);return new JSONArray();}
    }
    static boolean ensureCreated(JSONArray tasks){boolean changed=false;long now=System.currentTimeMillis();for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t!=null&&t.optLong("createdAt",0)<=0){try{t.put("createdAt",now+i);changed=true;}catch(JSONException ignored){}}}return changed;}
    static void save(Context c,JSONArray tasks){ensureCreated(tasks);c.getSharedPreferences("schedule",0).edit().putString("tasks",tasks.toString()).commit();schedule(c,tasks);}
    static long due(JSONObject t){return t.optBoolean("mustFinishToday")?deadline(t):at(t.optString("date"),t.optString("time"));}
    static boolean expired(JSONObject t,long now){return !t.optBoolean("completed")&&due(t)>0&&due(t)<=now;}
    static long at(String date,String time){try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);f.setTimeZone(ZONE);f.setLenient(false);String value=date+" "+time;java.text.ParsePosition p=new java.text.ParsePosition(0);Date d=f.parse(value,p);return d!=null&&p.getIndex()==value.length()?d.getTime():-1;}catch(Exception e){return -1;}}
    static long deadline(JSONObject t){long start=at(t.optString("date"),"00:00");return start<0?-1:start+86400000L;}
    static String today(){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(ZONE);return f.format(new Date());}
    static String countdown(long millis){long s=Math.max(0,(millis+999)/1000),d=s/86400,h=(s%86400)/3600,m=(s%3600)/60;return(d>0?d+"天 ":"")+(h>0?h+"小时 ":"")+String.format(Locale.CHINA,"%02d分 %02d秒",m,s%60);}
    private static PendingIntent alarm(Context c,String id,String phase,String title,String message){Intent i=new Intent(c,ScheduleReceiver.class).setAction("cn.livingroom.flipclock.TASK_ALARM").setData(android.net.Uri.parse("livingroom://schedule/"+id+"/"+phase)).putExtra("taskId",id).putExtra("phase",phase).putExtra("message",message).putExtra("title",title);return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void schedule(Context c,JSONArray tasks){
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        for(int i=0;i<tasks.length();i++){
            JSONObject t=tasks.optJSONObject(i);if(t==null)continue;
            String id=t.optString("id"),title=t.optString("title"),date=t.optString("date");
            for(String phase:new String[]{"departure","start"}){
                String time=t.optString(phase.equals("departure")?"departureTime":"time");
                String msg=phase.equals("departure")?time+" 该出发了，"+t.optString("time")+" 接送":time+" 到时间了"+(t.optBoolean("mustFinishToday")?"，今天一定要完成":"");
                PendingIntent pending=alarm(c,id,phase,title,msg);manager.cancel(pending);
                long when=time.isEmpty()?-1:at(date,time);
                if(!t.optBoolean("completed")&&when>System.currentTimeMillis())manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending);
            }
        }
    }
}
