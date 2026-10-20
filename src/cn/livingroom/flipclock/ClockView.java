package cn.livingroom.flipclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.*;

public class ClockView extends View {
    public String weather="正在获取天气…",place="上海 · 宝山";
    public long weatherTime;
    public boolean offline;
    private final Paint paint=new Paint(3);
    private final Typeface digits;
    private final Typeface regular=Typeface.create("sans-serif-light",Typeface.NORMAL);
    private final SharedPreferences prefs;
    private final android.icu.util.ChineseCalendar lunar=new android.icu.util.ChineseCalendar();
    private String hour,minute,oldHour,oldMinute;
    private long flipStart;
    private final long created=SystemClock.elapsedRealtime();
    private final Runnable tick=()->invalidate();
    public ClockView(Context c) {super(c);
        Typeface face;
        try {
            face=Typeface.createFromAsset(c.getAssets(),"fonts/Fliqlo.otf");
        }catch(RuntimeException missingPrivateFont){
          try {
            if(android.os.Build.VERSION.SDK_INT>=26)face=new Typeface.Builder(c.getAssets(),"fonts/Oswald.ttf").setFontVariationSettings("'wght' 600").setWeight(600).build();
            else face=Typeface.create(Typeface.createFromAsset(c.getAssets(),"fonts/Oswald.ttf"),Typeface.BOLD);
          }catch(RuntimeException e){face=Typeface.create("sans-serif-condensed",Typeface.BOLD);}
        }
        digits=face;
        prefs=c.getSharedPreferences("clock",0);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
    protected void onDetachedFromWindow() {removeCallbacks(tick);super.onDetachedFromWindow();}
    protected void onDraw(Canvas c) {
        super.onDraw(c); removeCallbacks(tick);
        Calendar cal=Calendar.getInstance(); int h=cal.get(Calendar.HOUR_OF_DAY);
        String nh=String.format(Locale.US,"%02d",h), nm=String.format(Locale.US,"%02d",cal.get(Calendar.MINUTE));
        long now=SystemClock.elapsedRealtime();
        if(hour==null) {hour=oldHour=nh;minute=oldMinute=nm;}
        else if(!hour.equals(nh)||!minute.equals(nm)) {oldHour=hour;oldMinute=minute;hour=nh;minute=nm;flipStart=now;}
        float progress=Math.min(1,(now-flipStart)/650f);
        c.drawColor(Color.BLACK);
        float scale=getHeight()/1080f;
        float canvasWidth=getWidth()/scale, center=canvasWidth/2;
        boolean compact=canvasWidth<1500;
        c.save(); c.scale(scale,scale);
        // Shift every five minutes to avoid keeping the same pixels illuminated.
        long slot=System.currentTimeMillis()/300000;
        if(prefs.getBoolean("pixelShift",true))c.translate((slot%5-2)*4,((slot/5)%5-2)*3);
        float alpha=prefs.getInt("brightness",100)/100f;
        if(prefs.getBoolean("nightDim",true)&&(h>=23||h<7))alpha*=.57f;
        c.saveLayerAlpha(0,0,canvasWidth,1080,(int)(alpha*255));
        float cardWidth=compact?(canvasWidth-128-36)/2:570;
        float cardHeight=compact?Math.min(410,cardWidth*.78f):410;
        float gap=compact?36:64;
        card(c,center-cardWidth-gap/2,270,cardWidth,cardHeight,hour,oldHour,progress);
        card(c,center+gap/2,270,cardWidth,cardHeight,minute,oldMinute,progress);
        String dateLine=prefs.getBoolean("showDate",true)?new SimpleDateFormat("yyyy年M月d日  EEEE",Locale.SIMPLIFIED_CHINESE).format(cal.getTime()):"";
        if(prefs.getBoolean("showLunar",true)&&!compact)dateLine+=(dateLine.isEmpty()?"":"     ·     ")+lunarDate(cal.getTimeInMillis());
        if(!dateLine.isEmpty())text(c,dateLine,center,compact?748:779,32,0xff8e9399,regular);
        if(compact&&prefs.getBoolean("showLunar",true))text(c,lunarDate(cal.getTimeInMillis()),center,798,30,0xff8e9399,regular);
        if(prefs.getBoolean("showWeather",true)) {
        String weatherLine=place+"     "+weather;
        paint.setTypeface(regular);paint.setTextSize(30);
        float iconX=center-paint.measureText(weatherLine)/2-45;
        if(weatherTime>0)weatherIcon(c,iconX,852);
        text(c,weatherLine,center,864,30,0xffb6bbc2,regular);
        String update=weatherTime==0?"定位不可用时使用上海市宝山区":
            (offline?"离线 · ":"")+new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date(weatherTime))+" 更新 · Open-Meteo";
        if(weatherTime>0&&System.currentTimeMillis()-weatherTime>3600000)update="天气可能已过期 · "+update;
        text(c,update,center,914,18,0xff555b63,regular);
        }
        if(now-created>=12000&&!compact)text(c,"↓ 应用",center,1016,20,0xff626970,regular);
        if(now-created<12000)text(c,compact?"返回收起侧栏":"左键日程     右键冰箱     下键应用     菜单键设置",center,1016,20,0xff626970,regular);
        c.restore(); c.restore();
        postDelayed(tick,progress<1?16:Math.max(100,1000-System.currentTimeMillis()%1000));
    }
    private String lunarDate(long time) {
        lunar.setTimeInMillis(time);
        int month=lunar.get(android.icu.util.Calendar.MONTH), day=lunar.get(android.icu.util.Calendar.DAY_OF_MONTH);
        String[] months={"正","二","三","四","五","六","七","八","九","十","冬","腊"};
        String[] numbers={"一","二","三","四","五","六","七","八","九","十"};
        String date=day==10?"初十":day==20?"二十":day==30?"三十":
            (day<10?"初":day<20?"十":"廿")+numbers[(day-1)%10];
        return "农历 "+(lunar.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH)==1?"闰":"")+months[month]+"月"+date;
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,Typeface font) {
        paint.setTypeface(font);paint.setTextSize(size);paint.setColor(color);paint.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,paint);
    }
    private void weatherIcon(Canvas c,float x,float y) {
        paint.setColor(0xffb6bbc2); paint.setStrokeWidth(2); paint.setStyle(Paint.Style.STROKE);
        if(weather.startsWith("晴")) {
            c.drawCircle(x,y,8,paint);
            for(int i=0;i<8;i++) { double a=i*Math.PI/4; c.drawLine(x+(float)Math.cos(a)*12,y+(float)Math.sin(a)*12,x+(float)Math.cos(a)*17,y+(float)Math.sin(a)*17,paint); }
        } else {
            Path cloud=new Path();cloud.moveTo(x-15,y+5);cloud.cubicTo(x-24,y-6,x-12,y-15,x-5,y-8);
            cloud.cubicTo(x-1,y-23,x+19,y-17,x+16,y-4);cloud.cubicTo(x+30,y-2,x+23,y+10,x+15,y+8);
            cloud.lineTo(x-15,y+8);c.drawPath(cloud,paint);
            if(weather.contains("雨"))for(int i=-1;i<=1;i++)c.drawLine(x+i*8,y+13,x+i*8-3,y+20,paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
    private void number(Canvas c,float x,float y,float w,float h,String value,boolean top) {
        c.save(); c.clipRect(x,y+(top?0:h/2),x+w,y+(top?h/2:h));
        paint.setTypeface(digits);paint.setTextSize(1000);paint.setTextScaleX(1);
        Rect bounds=new Rect();paint.getTextBounds("0123456789",0,10,bounds);
        float widest=0;for(int d=0;d<10;d++)widest=Math.max(widest,paint.measureText(""+d+d));
        float size=1000*Math.min(h*.82f/bounds.height(),w*.84f/widest);
        paint.setTextSize(size);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(top?0xffececea:0xffd9dad8);
        paint.getTextBounds("0123456789",0,10,bounds);float baseline=y+h/2-(bounds.top+bounds.bottom)/2f;
        c.drawText(value,x+w/2,baseline,paint);c.restore();
    }
    private void card(Canvas c,float x,float y,float w,float h,String value,String old,float p) {
        paint.setColor(0xff18191b);c.drawRoundRect(x,y,x+w,y+h,18,18,paint);
        paint.setColor(0xff202123);c.drawRoundRect(x,y,x+w,y+h/2+8,18,18,paint);
        paint.setColor(0xff18191b);c.drawRect(x,y+h/2,x+w,y+h/2+16,paint);
        if(value.equals(old)||p>=1) { number(c,x,y,w,h,value,true);number(c,x,y,w,h,value,false); }
        else {
            number(c,x,y,w,h,value,true);number(c,x,y,w,h,old,false);
            c.save();c.scale(1,Math.abs(1-2*p),x+w/2,y+h/2);
            boolean top=p<.5f;paint.setColor(top?0xff202123:0xff18191b);
            c.drawRect(x,y+(top?0:h/2),x+w,y+(top?h/2:h),paint);
            number(c,x,y,w,h,top?old:value,top);c.restore();
        }
        paint.setColor(0xff070708);c.drawRect(x,y+h/2-2,x+w,y+h/2+2,paint);
        paint.setColor(0xff38393a);c.drawRoundRect(x-2,y+h/2-12,x+7,y+h/2+12,3,3,paint);
        c.drawRoundRect(x+w-7,y+h/2-12,x+w+2,y+h/2+12,3,3,paint);
    }
}
