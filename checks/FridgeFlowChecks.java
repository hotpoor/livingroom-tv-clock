package cn.livingroom.flipclock;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Integration fixtures live only in a separate temporary fridge, removed in finally. */
public class FridgeFlowChecks extends Instrumentation {
    private Activity screen;
    private final String fridgeId="voice-check-"+UUID.randomUUID().toString();
    private final String first="batch-a",second="batch-b";
    private String familyBefore;
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void ui(Runnable task){Throwable[] error={null};super.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError("UI check failed",error[0]);}
    private void collect(View view,ArrayList<View> out){out.add(view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),out);}}
    private ArrayList<View> views(){ArrayList<View> out=new ArrayList<>();collect(screen.getWindow().getDecorView(),out);return out;}
    private View tag(String tag){for(View v:views())if(tag.equals(v.getTag()))return v;return null;}
    private void click(String label){ui(()->{for(View v:views())if(v instanceof Button&&((Button)v).getText().toString().equals(label)){v.performClick();return;}throw new AssertionError("Missing button "+label);});waitForIdleSync();}
    private void select(String tag,String contains){ui(()->{Spinner s=(Spinner)tag(tag);for(int i=0;i<s.getCount();i++)if(s.getItemAtPosition(i).toString().contains(contains)){s.setSelection(i);return;}throw new AssertionError("Missing option "+contains);});waitForIdleSync();}
    private JSONObject fixture() throws Exception{return InventoryStore.fridge(InventoryStore.load(getTargetContext()).data,fridgeId);}
    private String inventory() throws Exception{return InventoryStore.load(getTargetContext()).data.toString();}
    private JSONObject item(String id) throws Exception{return InventoryStore.item(fixture(),id);}
    private void open(String text){
        screen=startActivitySync(new Intent(getTargetContext(),VoiceFridgeActivity.class).putExtra("fridgeId",fridgeId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();ui(()->((EditText)tag("utterance")).setText(text));click("解析并核对");
        long until=SystemClock.uptimeMillis()+22000;boolean[] ready={false};while(SystemClock.uptimeMillis()<until){ui(()->ready[0]=tag("batch")!=null);if(ready[0]){waitForIdleSync();return;}SystemClock.sleep(100);}throw new AssertionError("Fridge draft timed out");
    }
    private void dialogButton(String label){
        // Dialog views are separate roots; use UiAutomation accessibility nodes.
        long until=SystemClock.uptimeMillis()+6000;boolean clicked=false;
        while(!clicked&&SystemClock.uptimeMillis()<until){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null){java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(label);for(android.view.accessibility.AccessibilityNodeInfo node:nodes)if(label.contentEquals(node.getText())){clicked=node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);break;}}
            if(!clicked)SystemClock.sleep(100);
        }check(clicked,"Dialog button missing: "+label);waitForIdleSync();
    }
    private void fixtureSetup() throws Exception{
        InventoryStore.Snapshot snapshot=InventoryStore.load(getTargetContext());familyBefore=snapshot.data.toString();
        JSONArray items=new JSONArray();for(int i=0;i<2;i++)items.put(new JSONObject().put("id",i==0?first:second).put("name","测试酸奶").put("quantity",i==0?5:2).put("unit","盒").put("location","上部冷藏").put("shelfLife",new JSONObject().put("startDate","2026-10-01").put("endDate",i==0?"2027-01-10":"2027-02-10")));
        snapshot.data.getJSONArray("fridges").put(new JSONObject().put("id",fridgeId).put("name","语音测试冰箱").put("items",items));InventoryStore.save(getTargetContext(),snapshot.data);
    }
    public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;try{
        fixtureSetup();String before=inventory();
        open("放进了两盒酸奶");ui(()->{check(((EditText)tag("food")).getText().toString().equals("测试酸奶"),"Inventory context did not expand alias");check(((Spinner)tag("location")).getSelectedItem().toString().equals("上部冷藏"),"Context location not filled");check(((EditText)tag("endDate")).getText().length()==0,"New batch inherited expiry");});click("取消");check(inventory().equals(before),"Context draft mutated inventory");
        open("了两盒测试酸奶");ui(()->{check(((EditText)tag("food")).getText().toString().equals("测试酸奶"),"Truncated voice particle remained in food");check(((Spinner)tag("operation")).getSelectedItemPosition()==0,"Missing action was invented");check(((Spinner)tag("location")).getSelectedItem().toString().equals("上部冷藏"),"Known food location not suggested");});click("取消");check(inventory().equals(before),"Truncated draft mutated inventory");
        open("放进了三盒测试苹果");select("location","上部冷藏");
        check(inventory().equals(before),"Parsing mutated inventory");click("核对变更");check(inventory().equals(before),"Preview mutated inventory");dialogButton("返回修改");check(inventory().equals(before),"Returning from preview mutated inventory");
        click("核对变更");dialogButton("确认更新");JSONObject apples=null;JSONArray items=fixture().getJSONArray("items");for(int i=0;i<items.length();i++)if(items.getJSONObject(i).optString("name").equals("测试苹果"))apples=items.getJSONObject(i);
        check(apples!=null&&apples.optInt("quantity")==3,"New stock not saved exactly once");String appleId=apples.optString("id");
        open("吃掉了两盒测试酸奶");ui(()->check(((Spinner)tag("batch")).getSelectedItemPosition()==0,"Ambiguous batch selected automatically"));select("batch","2027-01-10");click("核对变更");dialogButton("确认更新");check(item(first).getInt("quantity")==3&&item(second).getInt("quantity")==2,"Wrong batch deducted");
        before=inventory();open("吃掉了九十九盒测试酸奶");select("batch","2027-01-10");click("核对变更");check(inventory().equals(before),"Insufficient stock mutated");click("取消");check(inventory().equals(before),"Cancel mutated inventory");
        open("吃掉了三个测试酸奶");ui(()->check(((Spinner)tag("batch")).getSelectedItemPosition()==0,"Unit mismatch auto selected"));click("取消");
        open("吃掉了三盒测试酸奶");select("batch","2027-01-10");click("核对变更");dialogButton("确认更新");check(item(first)==null&&item(second).getInt("quantity")==2,"Zero stock not removed correctly");
        open("放进了两盒测试苹果");select("batch","测试苹果");click("核对变更");dialogButton("确认更新");check(item(appleId).getInt("quantity")==5,"Restock wrong quantity");
        before=inventory();open("放进了一个冲突测试食材");select("location","冷冻");click("核对变更");InventoryStore.save(getTargetContext(),InventoryStore.load(getTargetContext()).data);dialogButton("确认更新");check(inventory().equals(before),"Stale preview changed inventory");click("取消");
        result.putString("stream","Fridge flow passed: draft/preview/cancel, new stock, exact batch deduction, insufficient stock, units, zero removal, restock, stale revision.\n");code=Activity.RESULT_OK;
    }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}finally{
        try{InventoryStore.Snapshot snapshot=InventoryStore.load(getTargetContext());JSONArray all=snapshot.data.getJSONArray("fridges"),keep=new JSONArray();for(int i=0;i<all.length();i++)if(!fridgeId.equals(all.getJSONObject(i).optString("id")))keep.put(all.getJSONObject(i));snapshot.data.put("fridges",keep);InventoryStore.save(getTargetContext(),snapshot.data);if(familyBefore!=null&&!familyBefore.equals(snapshot.data.toString())){code=Activity.RESULT_CANCELED;result.putString("stream","Family inventory changed unexpectedly; investigate.\n");}}catch(Exception e){code=Activity.RESULT_CANCELED;result.putString("stream","Fixture cleanup failed.\n"+e.getClass().getSimpleName());}
        if(screen!=null&&!screen.isFinishing())ui(()->screen.finish());finish(code,result);
    }}
}
