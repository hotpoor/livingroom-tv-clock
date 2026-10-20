package cn.livingroom.flipclock;
import android.service.dreams.DreamService;
public class ClockDream extends DreamService {
    private Weather weather;
    public void onAttachedToWindow() {
        super.onAttachedToWindow(); InventorySync.start(this); setInteractive(false); setFullscreen(true); setScreenBright(true);
        ClockView view = new ClockView(this); setContentView(view); weather=new Weather(this,view);
    }
    public void onDreamingStarted() { super.onDreamingStarted(); weather.start(); }
    public void onDreamingStopped() { weather.stop(); super.onDreamingStopped(); }
    public void onDetachedFromWindow() { weather.close(); super.onDetachedFromWindow(); }
}
