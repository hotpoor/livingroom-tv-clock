package cn.livingroom.flipclock;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.util.Log;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class Weather {
    private final Context context;
    private final ClockView view;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private LocationManager manager;
    private LocationListener listener;
    private boolean active, fetching;
    private long generation;
    private final Runnable repeat=()->refresh();
    private final Runnable timeout=()->{ removeLocation(); fetch(null); };
    Weather(Context c,ClockView v) {
        context=c; view=v;
        view.weather=c.getSharedPreferences("clock",0).getString("weather","正在获取天气…");
        view.weatherTime=c.getSharedPreferences("clock",0).getLong("weatherTime",0);
        view.place=c.getSharedPreferences("clock",0).getString("place","上海 · 宝山");
    }
    public void start() { if(active)return; active=true; refresh(); }
    public void stop() { active=false; generation++; fetching=false; handler.removeCallbacksAndMessages(null); removeLocation(); }
    public void forceRefresh() { stop(); start(); }
    public void close() { stop(); executor.shutdownNow(); }
    private void refresh() {
        if(!active||fetching)return;
        fetching=true;
        if(context.getSharedPreferences("clock",0).getBoolean("auto",true)
           && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
            manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            Location best=null;
            try {
                for(String provider:manager.getProviders(true)) {
                    Location l=manager.getLastKnownLocation(provider);
                    if(valid(l) && (best==null||l.getTime()>best.getTime()))best=l;
                }
                if(best!=null) { fetch(best); return; }
                listener=new LocationListener() {
                    public void onLocationChanged(Location l) { if(valid(l)) { removeLocation(); fetch(l); } }
                    public void onStatusChanged(String p,int s,Bundle b) {}
                    public void onProviderEnabled(String p) {}
                    public void onProviderDisabled(String p) {}
                };
                for(String p:new String[]{LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER})
                    if(manager.isProviderEnabled(p)) manager.requestLocationUpdates(p,0,0,listener,Looper.getMainLooper());
                handler.postDelayed(timeout,12000); return;
            } catch(Exception e) { Log.i("FlipClock","Location unavailable: "+e); removeLocation(); }
        }
        fetch(null);
    }
    private boolean valid(Location l) { return l!=null && !(l.getLatitude()==0 && l.getLongitude()==0)
        && l.hasAccuracy() && l.getAccuracy()<=50000 && System.currentTimeMillis()-l.getTime()<86400000L; }
    private void removeLocation() {
        handler.removeCallbacks(timeout);
        if(manager!=null&&listener!=null) try { manager.removeUpdates(listener); } catch(Exception ignored){}
        listener=null;
    }
    private void fetch(Location location) {
        if(!active)return;
        final long token=generation;
        executor.execute(()-> {
            String place="上海 · 宝山";
            double lat=31.406,lon=121.489;
            if(location!=null) {
                lat=location.getLatitude(); lon=location.getLongitude(); place="当前位置";
                if(Geocoder.isPresent())try {
                    List<Address> list=new Geocoder(context,Locale.SIMPLIFIED_CHINESE).getFromLocation(lat,lon,1);
                    if(list!=null&&!list.isEmpty()) {
                        Address a=list.get(0);
                        String city=a.getLocality()!=null?a.getLocality():a.getAdminArea();
                        String district=a.getSubLocality()!=null?a.getSubLocality():a.getSubAdminArea();
                        if(city!=null) place=city+(district!=null?" · "+district:"");
                    }
                } catch(Exception ignored){}
            }
            String text=null;
            try {
                String endpoint=String.format(Locale.US,"https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",lat,lon);
                HttpURLConnection conn=(HttpURLConnection)new URL(endpoint).openConnection();
                conn.setConnectTimeout(12000); conn.setReadTimeout(12000);
                try {
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    try(InputStream in=conn.getInputStream()) { byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1)out.write(buf,0,n); }
                    JSONObject j=new JSONObject(out.toString("UTF-8")), current=j.getJSONObject("current"), daily=j.getJSONObject("daily");
                    text=description(current.getInt("weather_code"))+"   "+Math.round(current.getDouble("temperature_2m"))+"°"+
                        "     "+Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0))+"° / "+Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0))+"°";
                } finally {conn.disconnect();}
            } catch(Exception e) {Log.w("FlipClock","Weather request failed",e);}
            final String result=text,label=place;
            handler.post(()-> {
                if(!active||token!=generation)return;
                fetching=false;
                if(result!=null) {
                    long now=System.currentTimeMillis(); view.weather=result; view.place=label; view.weatherTime=now; view.offline=false;
                    context.getSharedPreferences("clock",0).edit().putString("weather",result).putString("place",label).putLong("weatherTime",now).apply();
                    Log.i("FlipClock","Weather updated: "+label+" "+result);
                } else {view.offline=true; if(view.weatherTime==0) {view.weather="天气暂不可用";view.place=label;} }
                view.invalidate(); handler.postDelayed(repeat,result==null?120000:
                    context.getSharedPreferences("clock",0).getInt("refreshMinutes",30)*60000L);
            });
        });
    }
    private static String description(int code) {
        if(code==0)return "晴"; if(code<=2)return "多云"; if(code==3)return "阴";
        if(code<=48)return "雾"; if(code<=57)return "毛毛雨"; if(code<=67)return "雨";
        if(code<=77)return "雪"; if(code<=82)return "阵雨"; if(code<=86)return "阵雪"; return "雷雨";
    }
}
