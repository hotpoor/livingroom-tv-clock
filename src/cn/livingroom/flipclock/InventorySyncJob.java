package cn.livingroom.flipclock;
import android.app.job.*;
public class InventorySyncJob extends JobService {
    public boolean onStartJob(JobParameters params){InventorySync.request(getApplicationContext(),()->new android.os.Handler(android.os.Looper.getMainLooper()).post(()->jobFinished(params,false)));return true;}
    public boolean onStopJob(JobParameters params){return true;}
}
