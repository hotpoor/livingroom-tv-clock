package cn.livingroom.flipclock;

import android.content.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Local inventory mutations, checked against the revision shown in the preview. */
public final class InventoryStore {
    static class Snapshot {
        final JSONObject data;final long revision;
        Snapshot(JSONObject d,long r){data=d;revision=r;}
    }
    static synchronized Snapshot load(Context c) throws Exception {
        SharedPreferences p=c.getSharedPreferences("fridges",0);
        String raw=p.getString("inventory",null);
        if(raw==null){ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getAssets().open("inventory.json")){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}raw=out.toString("UTF-8");}
        JSONObject data=new JSONObject(raw);JSONArray fridges=data.getJSONArray("fridges");
        for(int f=0;f<fridges.length();f++){
            JSONObject fridge=fridges.getJSONObject(f);JSONArray items=fridge.optJSONArray("items");
            if(items==null){items=new JSONArray();fridge.put("items",items);}
            for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(item.optString("id").isEmpty())item.put("id",UUID.nameUUIDFromBytes((fridge.optString("id")+"#"+i+"#"+item.toString()).getBytes("UTF-8")).toString());}
        }
        return new Snapshot(data,p.getLong("inventoryRevision",0));
    }
    static synchronized void save(Context c,JSONObject data){SharedPreferences p=c.getSharedPreferences("fridges",0);p.edit().putString("inventory",data.toString()).putLong("inventoryRevision",p.getLong("inventoryRevision",0)+1).apply();}
    static JSONObject fridge(JSONObject data,String id){JSONArray list=data.optJSONArray("fridges");if(list!=null)for(int i=0;i<list.length();i++){JSONObject f=list.optJSONObject(i);if(f!=null&&id.equals(f.optString("id")))return f;}return null;}
    static JSONObject item(JSONObject fridge,String id){JSONArray list=fridge.optJSONArray("items");if(list!=null)for(int i=0;i<list.length();i++){JSONObject t=list.optJSONObject(i);if(t!=null&&id.equals(t.optString("id")))return t;}return null;}
    static String dateError(String start,String end){
        if(!start.isEmpty()&&(end.isEmpty()||!start.matches("\\d{4}-\\d{2}-\\d{2}")||ScheduleStore.at(start,"00:00")<0))return "请填写有效的保质期开始和到期日期";
        if(!end.isEmpty()&&(!end.matches("\\d{4}-\\d{2}-\\d{2}")||ScheduleStore.at(end,"00:00")<0))return "请填写有效的到期日期";
        return !start.isEmpty()&&start.compareTo(end)>0?"开始日期不能晚于到期日期":null;
    }
    static synchronized int apply(Context c,long revision,String fridgeId,String itemId,String action,int delta,String name,String unit,String location,String start,String end) throws Exception {
        Snapshot current=load(c);
        if(current.revision!=revision)throw new IllegalStateException("库存已发生变化，请重新核对");
        JSONObject fridge=fridge(current.data,fridgeId);if(fridge==null)throw new IllegalArgumentException("请选择有效的冰箱");
        if(!action.equals("add")&&!action.equals("remove"))throw new IllegalArgumentException("请选择操作");
        if(delta<1||delta>9999)throw new IllegalArgumentException("数量应为 1 到 9999 的整数");
        JSONArray items=fridge.getJSONArray("items");JSONObject existing=itemId.isEmpty()?null:item(fridge,itemId);
        if(!itemId.isEmpty()&&existing==null)throw new IllegalStateException("这批食材已变化，请重新选择");
        if(action.equals("remove")&&existing==null)throw new IllegalArgumentException("请先选择要扣减的食材批次");
        int before=existing==null?0:existing.optInt("quantity"),after=action.equals("remove")?before-delta:before+delta;
        if(after<0)throw new IllegalArgumentException("库存不足，当前只有 "+before+existing.optString("unit"));
        if(after>999999)throw new IllegalArgumentException("库存数量过大，请核对");
        if(existing==null){
            if(name.trim().isEmpty()||name.length()>80||unit.trim().isEmpty()||unit.length()>8||location.trim().isEmpty())throw new IllegalArgumentException("请补全食材、单位和分区");
            String error=dateError(start,end);if(error!=null)throw new IllegalArgumentException(error);
            JSONObject t=new JSONObject();t.put("id",UUID.randomUUID().toString());t.put("name",name.trim());t.put("quantity",after);t.put("unit",unit.trim());t.put("location",location);
            t.put("shelfLife",end.isEmpty()?JSONObject.NULL:new JSONObject().put("startDate",start).put("endDate",end));if(end.isEmpty())t.put("reminder","尽快食用");items.put(t);
        }else if(after==0){for(int i=0;i<items.length();i++)if(itemId.equals(items.getJSONObject(i).optString("id"))){items.remove(i);break;}}
        else existing.put("quantity",after);
        fridge.put("inventoryStatus","已部分录入");save(c,current.data);return after;
    }
}
