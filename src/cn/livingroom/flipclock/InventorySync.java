package cn.livingroom.flipclock;

import android.app.job.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Full snapshots use server CAS; divergent edits are retained for web review. */
public final class InventorySync {
    static final String CHANGED="cn.livingroom.flipclock.INVENTORY_CHANGED",STATUS="cn.livingroom.flipclock.INVENTORY_SYNC_STATUS";
    private static final Handler handler=new Handler(Looper.getMainLooper());
    private static boolean started,running,requestAgain;
    static volatile boolean pausedForChecks;
    private static Context app;
    private static final Runnable ticker=new Runnable(){public void run(){request(app,null);handler.postDelayed(this,300000);}};
    static JSONObject config(Context c) throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getAssets().open("schedule-service.json")){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}
    static String status(Context c){return c.getSharedPreferences("fridges",0).getString("syncStatus","正在连接网页清单…");}
    static synchronized void start(Context c){app=c.getApplicationContext();if(!started){started=true;InventoryEvents.start(app);handler.post(ticker);JobScheduler jobs=(JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(jobs.getPendingJob(67108)==null)jobs.schedule(new JobInfo.Builder(67108,new ComponentName(app,InventorySyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build());}}
    static void trigger(Context c){start(c);request(c.getApplicationContext(),null);}
    private static void status(Context c,String value){android.content.SharedPreferences p=c.getSharedPreferences("fridges",0);if(!value.equals(p.getString("syncStatus",""))){p.edit().putString("syncStatus",value).apply();c.sendBroadcast(new Intent(STATUS).setPackage(c.getPackageName()));}}
    static synchronized void request(Context c,Runnable done){
        if(pausedForChecks){if(done!=null)done.run();return;}if(running){requestAgain=true;if(done!=null)done.run();return;}running=true;
        new Thread(()->{HttpURLConnection connection=null;try{
            JSONObject configuration=config(c);String url=configuration.optString("url"),token=configuration.optString("token");if(url.isEmpty()||token.isEmpty()){status(c,"本机清单 · 尚未配置网页同步");return;}
            android.content.SharedPreferences p=c.getSharedPreferences("fridges",0);final long localRevision,base;final boolean dirty;final String change;JSONObject body;
            synchronized(InventoryStore.class){InventoryStore.Snapshot snapshot=InventoryStore.load(c);localRevision=snapshot.revision;base=p.getLong("serverRevision",-1);dirty=p.getBoolean("inventoryDirty",base<0&&p.contains("inventory"));String id=p.getString("syncDeviceId","");if(id.isEmpty()){id=UUID.randomUUID().toString();p.edit().putString("syncDeviceId",id).apply();}String current=p.getString("syncChangeId","");if(dirty&&current.isEmpty()){current=UUID.randomUUID().toString();p.edit().putString("syncChangeId",current).apply();}change=current;body=new JSONObject().put("deviceId",id).put("baseRevision",base).put("dirty",dirty).put("changeId",change).put("inventory",snapshot.data);}
            connection=(HttpURLConnection)new URL(url+"/api/sync").openConnection();connection.setConnectTimeout(4000);connection.setReadTimeout(8000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setRequestProperty("Authorization","Bearer "+token);
            try(OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}int code=connection.getResponseCode();if(code!=200&&code!=409)throw new IOException("sync unavailable");ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=code==200?connection.getInputStream():connection.getErrorStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){bytes.write(b,0,n);if(bytes.size()>2*1024*1024)throw new IOException("large response");}}
            JSONObject response=new JSONObject(bytes.toString("UTF-8"));if(code==409){status(c,"两边都有修改 · 请在网页核对");return;}if(dirty&&!change.equals(response.optString("acknowledgedChangeId")))throw new IOException("missing acknowledgment");
            boolean changed=false;
            synchronized(InventoryStore.class){if(pausedForChecks||p.getLong("inventoryRevision",0)!=localRevision)return;JSONObject data=response.getJSONObject("inventory");data.getJSONArray("fridges");String raw=data.toString();changed=!raw.equals(p.getString("inventory",null));android.content.SharedPreferences.Editor edit=p.edit().putString("inventory",raw).putBoolean("inventoryDirty",false).putString("syncChangeId","").putLong("serverRevision",response.getLong("revision"));if(changed)edit.putLong("inventoryRevision",localRevision+1);if(!edit.commit())throw new IOException("sync persistence failed");}
            status(c,"与网页清单已同步");if(changed){c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));synchronized(InventorySync.class){requestAgain=true;}}
        }catch(Exception e){status(c,"网页暂未连接 · 本机修改已保留");}finally{if(connection!=null)connection.disconnect();boolean again;synchronized(InventorySync.class){running=false;again=requestAgain;requestAgain=false;}if(done!=null)done.run();if(again)request(c,null);}},"inventory-sync").start();
    }
}
