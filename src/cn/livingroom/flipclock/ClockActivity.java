package cn.livingroom.flipclock;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;

public class ClockActivity extends Activity {
    private ClockView clock;
    private Weather weather;
    private FrameLayout root;
    private FridgePanel fridge;
    private SchedulePanel schedule;
    private boolean foreground;
    private int dialogs;
    private long opened;
    private final BroadcastReceiver exit = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) { finish(); }
    };
    private final BroadcastReceiver alert = new BroadcastReceiver() {
        public void onReceive(Context c,Intent i){if(foreground){openSchedule();schedule.focusTask(i.getStringExtra("taskId"),i.getStringExtra("phase"));android.widget.Toast.makeText(c,i.getStringExtra("message"),android.widget.Toast.LENGTH_LONG).show();}}
    };
    public void onCreate(Bundle b) {
        super.onCreate(b);
        InventorySync.start(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        opened = SystemClock.elapsedRealtime();
        clock = new ClockView(this);
        root=new FrameLayout(this);root.addView(clock,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        weather = new Weather(this, clock);
        registerReceiver(exit, new IntentFilter("cn.livingroom.flipclock.EXIT"));
        registerReceiver(alert,new IntentFilter("cn.livingroom.flipclock.SCHEDULE_ALERT"));
        ScheduleStore.schedule(this,ScheduleStore.load(this));
        if("schedule".equals(getIntent().getStringExtra("openPanel")))root.post(()->{openSchedule();schedule.focusTask(getIntent().getStringExtra("taskId"),getIntent().getStringExtra("phase"));});
        if("voice".equals(getIntent().getStringExtra("openPanel")))root.post(()->openVoiceTest());
        if("fridgeVoice".equals(getIntent().getStringExtra("openPanel")))root.post(()->openFridgeVoice());
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && !getPreferences(0).getBoolean("asked", false)) {
            getPreferences(0).edit().putBoolean("asked", true).apply();
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }
    protected void onResume() { super.onResume();foreground=true; if (weather != null) weather.start(); if(schedule!=null)schedule.refresh(); if(fridge!=null)fridge.reload(); }
    protected void onPause() { foreground=false;super.onPause(); if (weather != null) weather.stop(); }
    protected void onDestroy() { weather.close(); unregisterReceiver(exit);unregisterReceiver(alert); super.onDestroy(); }
    protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);if("schedule".equals(i.getStringExtra("openPanel"))){openSchedule();schedule.focusTask(i.getStringExtra("taskId"),i.getStringExtra("phase"));}if("voice".equals(i.getStringExtra("openPanel")))openVoiceTest();if("fridgeVoice".equals(i.getStringExtra("openPanel")))openFridgeVoice();}
    public void onRequestPermissionsResult(int r, String[] p, int[] g) { super.onRequestPermissionsResult(r,p,g); weather.start(); }
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (dialogs > 0) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE || k == KeyEvent.KEYCODE_POWER)
            return super.dispatchKeyEvent(e);
        if(fridge!=null){
            if(e.getAction()==KeyEvent.ACTION_DOWN&&(k==KeyEvent.KEYCODE_BACK||k==KeyEvent.KEYCODE_MENU)){closeFridge();return true;}
            return super.dispatchKeyEvent(e);
        }
        if(schedule!=null){
            if(k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT){if(e.getAction()==KeyEvent.ACTION_DOWN)schedule.switchTab(k==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);return true;}
            if(e.getAction()==KeyEvent.ACTION_DOWN&&(k==KeyEvent.KEYCODE_BACK||k==KeyEvent.KEYCODE_MENU)){closeSchedule();return true;}
            return super.dispatchKeyEvent(e);
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && SystemClock.elapsedRealtime()-opened > 1000) {
            if (k == KeyEvent.KEYCODE_MENU) { settings(); return true; }
            if(k==KeyEvent.KEYCODE_DPAD_RIGHT){openFridge();return true;}
            if(k==KeyEvent.KEYCODE_DPAD_LEFT){openSchedule();return true;}
            // Xiaomi's original saver may be underneath this activity. Dismiss
            // it as well so a single remote key returns to the prior app.
            try { sendBroadcast(new Intent("com.duokan.intent.action.exit.screensaver")); } catch (SecurityException ignored) {}
            try { sendBroadcast(new Intent("finish screensaver").setPackage("com.xiaomi.tv.gallery")); } catch (SecurityException ignored) {}
            finish(); return true;
        }
        return true;
    }
    private void settings() {
        SharedPreferences p = getSharedPreferences("clock",0);
        show(new AlertDialog.Builder(this).setTitle("翻页时钟 · 设置")
          .setItems(new String[]{"天气设置", "屏幕亮度 · "+p.getInt("brightness",100)+"%", "时钟与日期 · 农历", "冰箱库存 · 横向 Tab", "备忘与日程 · 倒计时", "语音添加日程", "语音更新冰箱", "打开 YouTube 电视版", "返回时钟"},(d,w)-> {
              if(w==0) weatherSettings();
              if(w==1) brightnessSettings();
              if(w==2) displaySettings();
              if(w==3) openFridge();
              if(w==4) openSchedule();
              if(w==5) openVoiceTest();
              if(w==6) openFridgeVoice();
              if(w==7) openYouTube();
          }).setNegativeButton("关闭",null));
    }
    private void openYouTube(){
        Intent launch=getPackageManager().getLeanbackLaunchIntentForPackage("com.google.android.youtube.tv");
        if(launch==null)launch=getPackageManager().getLaunchIntentForPackage("com.google.android.youtube.tv");
        if(launch==null){android.widget.Toast.makeText(this,"尚未安装 YouTube 电视版",android.widget.Toast.LENGTH_LONG).show();return;}
        try{startActivity(launch);}catch(ActivityNotFoundException|SecurityException e){android.widget.Toast.makeText(this,"系统未允许打开 YouTube",android.widget.Toast.LENGTH_LONG).show();}
    }
    private void openVoiceTest(){startActivity(new Intent(this,VoiceScheduleActivity.class));}
    private void openFridgeVoice(){startActivity(new Intent(this,VoiceFridgeActivity.class));}
    private void openFridge(){
        if(fridge!=null)return;
        closeSchedule();
        int width=getResources().getDisplayMetrics().widthPixels;
        int panelWidth=width/3;
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(width-panelWidth,-1,Gravity.LEFT);clock.setLayoutParams(cp);
        fridge=new FridgePanel(this,()->closeFridge());
        root.addView(fridge,new FrameLayout.LayoutParams(panelWidth,-1,Gravity.RIGHT));fridge.focusTabs();
    }
    private void closeFridge(){
        if(fridge==null)return;root.removeView(fridge);fridge=null;clock.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));opened=SystemClock.elapsedRealtime();
    }
    private void openSchedule(){
        if(schedule!=null)return;closeFridge();
        int width=getResources().getDisplayMetrics().widthPixels,panelWidth=width/3;
        clock.setLayoutParams(new FrameLayout.LayoutParams(width-panelWidth,-1,Gravity.RIGHT));
        schedule=new SchedulePanel(this);root.addView(schedule,new FrameLayout.LayoutParams(panelWidth,-1,Gravity.LEFT));schedule.focusFirst();
    }
    private void closeSchedule(){
        if(schedule==null)return;root.removeView(schedule);schedule=null;clock.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));opened=SystemClock.elapsedRealtime();
    }
    private void show(AlertDialog.Builder builder) {
        dialogs++;
        AlertDialog a=builder.create();
        a.setOnDismissListener(d->{ dialogs=Math.max(0,dialogs-1); opened=SystemClock.elapsedRealtime(); clock.invalidate(); });
        a.show();
        a.getWindow().setDimAmount(.18f);
    }
    private void weatherSettings() {
        SharedPreferences p=getSharedPreferences("clock",0);
        show(new AlertDialog.Builder(this).setTitle("天气设置")
          .setItems(new String[]{"天气位置 · "+(p.getBoolean("auto",true)?"自动定位":"上海市宝山区"),
              "显示天气 · "+(p.getBoolean("showWeather",true)?"开启":"关闭"),
              "刷新间隔 · "+p.getInt("refreshMinutes",30)+" 分钟", "立即刷新天气"},(d,w)-> {
              if(w==0) locationSettings();
              if(w==1) {p.edit().putBoolean("showWeather",!p.getBoolean("showWeather",true)).apply();weatherSettings();}
              if(w==2) refreshSettings();
              if(w==3) {weather.forceRefresh(); android.widget.Toast.makeText(this,"正在刷新天气",android.widget.Toast.LENGTH_SHORT).show();}
          }).setNegativeButton("返回",(d,w)->settings()));
    }
    private void locationSettings() {
        SharedPreferences p=getSharedPreferences("clock",0);
        show(new AlertDialog.Builder(this).setTitle("天气位置")
          .setSingleChoiceItems(new String[]{"自动定位（失败使用上海市宝山区）", "固定使用上海市宝山区"},p.getBoolean("auto",true)?0:1,(d,w)-> {
              p.edit().putBoolean("auto",w==0).apply(); weather.forceRefresh(); d.dismiss(); weatherSettings();
          }).setNegativeButton("返回",(d,w)->weatherSettings()));
    }
    private void refreshSettings() {
        SharedPreferences p=getSharedPreferences("clock",0);
        int[] values={15,30,60}; int current=p.getInt("refreshMinutes",30), selected=current==15?0:current==60?2:1;
        show(new AlertDialog.Builder(this).setTitle("天气刷新间隔")
          .setSingleChoiceItems(new String[]{"15 分钟","30 分钟（推荐）","60 分钟"},selected,(d,w)-> {
              p.edit().putInt("refreshMinutes",values[w]).apply(); weather.forceRefresh();d.dismiss();weatherSettings();
          }).setNegativeButton("返回",(d,w)->weatherSettings()));
    }
    private void brightnessSettings() {
        SharedPreferences p=getSharedPreferences("clock",0);
        int[] values={100,80,60,40,20}; int selected=0;
        for(int i=0;i<values.length;i++)if(values[i]==p.getInt("brightness",100))selected=i;
        show(new AlertDialog.Builder(this).setTitle("屏幕亮度 · 仅影响屏保画面")
          .setSingleChoiceItems(new String[]{"100% · 明亮","80%","60% · 柔和","40%","20% · 微光"},selected,(d,w)-> {
              p.edit().putInt("brightness",values[w]).apply();clock.invalidate();
          }).setNeutralButton("夜间自动调暗",(d,w)->nightSettings()).setNegativeButton("完成",(d,w)->settings()));
    }
    private void nightSettings() {
        SharedPreferences p=getSharedPreferences("clock",0);
        show(new AlertDialog.Builder(this).setTitle("夜间自动调暗 · 23:00–07:00")
          .setSingleChoiceItems(new String[]{"开启 · 在所选亮度上继续调暗","关闭 · 始终使用所选亮度"},p.getBoolean("nightDim",true)?0:1,(d,w)-> {
              p.edit().putBoolean("nightDim",w==0).apply();clock.invalidate();d.dismiss();brightnessSettings();
          }).setNegativeButton("返回",(d,w)->brightnessSettings()));
    }
    private void displaySettings() {
        SharedPreferences p=getSharedPreferences("clock",0);
        String[] keys={"showDate","showLunar","pixelShift"};
        show(new AlertDialog.Builder(this).setTitle("时钟与日期")
          .setMultiChoiceItems(new String[]{"显示公历日期和星期","显示农历日期","定时轻微移动画面"},
              new boolean[]{p.getBoolean(keys[0],true),p.getBoolean(keys[1],true),p.getBoolean(keys[2],true)},(d,w,on)-> {
                  p.edit().putBoolean(keys[w],on).apply();clock.invalidate();
              }).setNegativeButton("完成",(d,w)->settings()));
    }
}
