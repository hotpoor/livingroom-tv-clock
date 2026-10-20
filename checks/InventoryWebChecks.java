package cn.livingroom.flipclock;
import android.app.*;
import android.os.*;
import org.json.*;

/** Only modifies the explicitly identified temporary web-check fridge. */
public class InventoryWebChecks extends Instrumentation {
    private String fixture;
    public void onCreate(Bundle args){super.onCreate(args);fixture=args.getString("fixtureId","");start();}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    private JSONObject waitFor(int quantity,boolean clean) throws Exception {
        long until=SystemClock.uptimeMillis()+30000;
        while(SystemClock.uptimeMillis()<until){InventoryStore.Snapshot snapshot=InventoryStore.load(getTargetContext());JSONObject f=InventoryStore.fridge(snapshot.data,fixture),item=f==null?null:InventoryStore.item(f,"web-check-item");if(item!=null&&item.optInt("quantity")==quantity&&(!clean||!getTargetContext().getSharedPreferences("fridges",0).getBoolean("inventoryDirty",true)))return item;InventorySync.trigger(getTargetContext());SystemClock.sleep(500);}
        throw new AssertionError("Web/TV sync timed out");
    }
    public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;try{
        check(fixture.startsWith("web-check-"),"Refusing a non-test fixture");InventorySync.start(getTargetContext());JSONObject item=waitFor(3,true);
        InventoryStore.Snapshot snapshot=InventoryStore.load(getTargetContext());InventoryStore.apply(getTargetContext(),snapshot.revision,fixture,"web-check-item","add",2,item.getString("name"),item.getString("unit"),item.getString("location"),"","");
        waitFor(5,true);result.putString("stream","Inventory sync passed: web change pulled to TV; confirmed TV change acknowledged by server.\n");code=Activity.RESULT_OK;
    }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));}finally{finish(code,result);}}
}
