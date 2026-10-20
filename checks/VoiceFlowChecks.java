package cn.livingroom.flipclock;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Explicit device integration checks; removes only its own saved test item. */
public class VoiceFlowChecks extends Instrumentation {
    private Activity screen;
    private String createdId;
    private final String title="集成测试事项";
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean good,String message){if(!good)throw new AssertionError(message);}
    private void ui(Runnable task){Throwable[] error={null};super.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError("UI check failed",error[0]);}
    private void collect(View v,ArrayList<View> out){out.add(v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),out);}}
    private ArrayList<View> views(){ArrayList<View> out=new ArrayList<>();collect(screen.getWindow().getDecorView(),out);return out;}
    private Button button(String label){for(View v:views())if(v instanceof Button&&((Button)v).getText().toString().equals(label))return (Button)v;throw new AssertionError("Missing button: "+label);}
    private ArrayList<EditText> fields(){ArrayList<EditText> out=new ArrayList<>();for(View v:views())if(v instanceof EditText)out.add((EditText)v);return out;}
    private void click(String label){ui(()->button(label).performClick());waitForIdleSync();}
    private void open(){screen=startActivitySync(new Intent(getTargetContext(),VoiceScheduleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();}
    private void enter(String text){ui(()->fields().get(0).setText(text));click("解析并核对");long until=SystemClock.uptimeMillis()+22000;boolean[] done={false};while(SystemClock.uptimeMillis()<until){ui(()->done[0]=fields().size()==4);if(done[0])return;SystemClock.sleep(100);}StringBuilder detail=new StringBuilder();ui(()->{for(View v:views())if(v instanceof TextView)detail.append(((TextView)v).getText()).append(" | ");});throw new AssertionError("Draft response timed out: "+detail);}
    public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;try{
        Context c=getTargetContext();int before=ScheduleStore.load(c).length();open();
        enter("明天下午四点十分"+title+"，下午四点出发");
        ui(()->{ArrayList<EditText> f=fields();check(f.get(0).getText().toString().contains(title),"Wrong title");java.util.Calendar tomorrow=java.util.Calendar.getInstance(ScheduleStore.ZONE);tomorrow.add(java.util.Calendar.DAY_OF_MONTH,1);java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US);format.setTimeZone(ScheduleStore.ZONE);check(f.get(1).getText().toString().equals(format.format(tomorrow.getTime())),"Wrong date");check(f.get(2).getText().toString().equals("16:10"),"Wrong start");check(f.get(3).getText().toString().equals("16:00"),"Wrong departure");});
        check(ScheduleStore.load(c).length()==before,"Parse saved without confirmation");
        click("确认保存");
        JSONArray saved=ScheduleStore.load(c);check(saved.length()==before+1,"Save did not add exactly one item");
        for(int i=0;i<saved.length();i++){JSONObject task=saved.getJSONObject(i);if(task.optString("title").contains(title))createdId=task.getString("id");}
        check(createdId!=null,"Saved test task missing");
        // Cancel a second complete draft: no additional task may be written.
        open();enter("后天早上八点半取消测试");click("取消");check(ScheduleStore.load(c).length()==before+1,"Cancel saved a task");
        // Missing time must remain blank and save must stay on the form.
        open();enter("明天缺少时间测试");ui(()->check(fields().get(2).getText().length()==0,"Missing time was invented"));click("确认保存");
        check(!screen.isFinishing(),"Missing time accepted");check(ScheduleStore.load(c).length()==before+1,"Invalid draft saved");
        ui(()->screen.finish());result.putString("stream","Voice flow passed: parsed fields, explicit save, cancel, missing-time rejection.\n");code=Activity.RESULT_OK;
    }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}finally{
        if(createdId!=null){JSONArray tasks=ScheduleStore.load(getTargetContext()),keep=new JSONArray();for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t!=null&&createdId.equals(t.optString("id"))){try{t.put("completed",true);}catch(Exception ignored){}ScheduleStore.schedule(getTargetContext(),new JSONArray().put(t));}else keep.put(t);}ScheduleStore.save(getTargetContext(),keep);}
        if(screen!=null&&!screen.isFinishing())ui(()->screen.finish());
        finish(code,result);
    }}
}
