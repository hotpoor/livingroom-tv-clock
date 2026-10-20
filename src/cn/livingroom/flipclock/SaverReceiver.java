package cn.livingroom.flipclock;
import android.content.*;
import android.util.Log;

public class SaverReceiver extends BroadcastReceiver {
    public void onReceive(Context c, Intent i) {
        Log.i("FlipClock", "Saver broadcast: " + i.getAction());
        if ("com.duokan.intent.action.show.screensaver".equals(i.getAction())) {
            c.getSharedPreferences("clock",0).edit().putLong("lastSaverTrigger",System.currentTimeMillis()).apply();
            c.startActivity(new Intent(c,ClockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } else {
            c.sendBroadcast(new Intent("cn.livingroom.flipclock.EXIT").setPackage(c.getPackageName()));
        }
    }
}
