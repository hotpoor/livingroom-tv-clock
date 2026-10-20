package cn.livingroom.flipclock;
import android.app.*;
import android.content.*;
import org.json.*;

public class ScheduleReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i){
        JSONArray tasks=ScheduleStore.load(c);
        if(!"cn.livingroom.flipclock.TASK_ALARM".equals(i.getAction())){ScheduleStore.schedule(c,tasks);return;}
        JSONObject task=null;for(int n=0;n<tasks.length();n++){JSONObject t=tasks.optJSONObject(n);if(t!=null&&t.optString("id").equals(i.getStringExtra("taskId")))task=t;}
        if(task==null||task.optBoolean("completed"))return;
        String title=i.getStringExtra("title"),message=i.getStringExtra("message");
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel=new NotificationChannel("schedule","日程提醒",NotificationManager.IMPORTANCE_HIGH);nm.createNotificationChannel(channel);
        Intent open=new Intent(c,ClockActivity.class).putExtra("openPanel","schedule").putExtra("taskId",task.optString("id")).putExtra("phase",i.getStringExtra("phase")).setData(i.getData()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent click=PendingIntent.getActivity(c,200,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,"schedule").setSmallIcon(c.getApplicationInfo().icon).setContentTitle(title).setContentText(message).setContentIntent(click).setAutoCancel(true).build();
        nm.notify(i.getDataString().hashCode(),n);
        c.sendBroadcast(new Intent("cn.livingroom.flipclock.SCHEDULE_ALERT").setPackage(c.getPackageName()).putExtra("taskId",task.optString("id")).putExtra("phase",i.getStringExtra("phase")).putExtra("message",title+"："+message));
        android.util.Log.i("FlipClock","Schedule alert: "+title+" "+message);
    }
}
