package cn.livingroom.flipclock;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Food voice drafts are read-only until the final explicit confirmation. */
public class VoiceFridgeActivity extends Activity {
    private LinearLayout root,content,recommendations;
    private EditText input,food,quantity,unit,start,end;
    private Spinner fridgeChoice,operation,batch,location,inputFridge;
    private TextView status,stock;
    private Button parse;
    private InventoryStore.Snapshot snapshot;
    private JSONArray fridges;
    private JSONObject draft;
    private final ArrayList<String> batchIds=new ArrayList<>(),locations=new ArrayList<>();
    private String utterance="",preferred="",selectedBatch="",autoLocation="";
    private String[] newValues;
    private boolean shownKeyboard,busy,saved,showingExisting;
    private int requestId;
    private static final int WHITE=0xffedf2f3,MUTED=0xffa8bac3;

    public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894);
        preferred=getIntent().getStringExtra("fridgeId");if(preferred==null)preferred="";
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(40),dp(20),dp(40),dp(16));root.setBackgroundColor(0xff121c22);setContentView(root);
        if(state!=null){utterance=state.getString("utterance","");preferred=state.getString("preferred",preferred);}inputScreen();
        if(state!=null&&state.containsKey("draft"))try{confirmScreen(new JSONObject(state.getString("draft")));}catch(Exception ignored){}
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);return v;}
    private void heading(String title,String hint){root.removeAllViews();TextView h=text(title,25,WHITE);h.setTypeface(null,Typeface.BOLD);root.addView(h);TextView sub=text(hint,13,MUTED);sub.setPadding(0,dp(5),0,dp(9));root.addView(sub);ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(16);b.setTextColor(WHITE);b.setAllCaps(false);StateListDrawable states=new StateListDrawable();for(boolean focus:new boolean[]{true,false}){GradientDrawable d=new GradientDrawable();d.setColor(focus?0xff365951:0xff263840);d.setCornerRadius(dp(8));d.setStroke(dp(2),focus?0xffb8efd9:0xff3a5059);states.addState(focus?new int[]{android.R.attr.state_focused}:new int[]{},d);}b.setBackground(states);return b;}
    private LinearLayout row(LinearLayout parent){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);parent.addView(r);return r;}
    private LinearLayout column(LinearLayout row){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(0,0,dp(12),0);row.addView(c,new LinearLayout.LayoutParams(0,-2,1));return c;}
    private EditText field(LinearLayout parent,String label,String tag,String value){parent.addView(text(label,12,MUTED));EditText e=new EditText(this);e.setTag(tag);e.setContentDescription(label);e.setSingleLine(true);e.setText(value);e.setTextSize(17);e.setTextColor(WHITE);e.setSelectAllOnFocus(true);e.setPadding(dp(9),dp(4),dp(9),dp(4));e.setBackgroundColor(0xff24353d);e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(38));p.bottomMargin=dp(7);parent.addView(e,p);return e;}
    private Spinner choice(LinearLayout parent,String label,String tag){parent.addView(text(label,12,MUTED));Spinner s=new Spinner(this,Spinner.MODE_DIALOG);s.setTag(tag);s.setContentDescription(label);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(39));p.bottomMargin=dp(6);parent.addView(s,p);return s;}
    private void options(Spinner spinner,List<String> labels){ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);}
    private void listen(Spinner spinner,Runnable listener){spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){listener.run();}});}
    private void action(LinearLayout row,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(43),1);p.setMargins(dp(3),dp(5),dp(3),0);row.addView(b,p);}
    private String value(JSONObject d,String key){return d.isNull(key)?"":d.optString(key);}
    private void inputScreen(){
        draft=null;busy=false;shownKeyboard=false;heading("说一句，更新冰箱清单","按住语音键，看到收音提示后说完整一句，例如「放进了两盒草莓酸奶」。");
        inputFridge=choice(content,"先选冰箱，帮助识别已有食材；也可以稍后选择","inputFridge");
        ArrayList<String> names=new ArrayList<>(),ids=new ArrayList<>();names.add("解析后选择冰箱");ids.add("");int selected=0;
        try{JSONArray fs=InventoryStore.load(this).data.getJSONArray("fridges");for(int i=0;i<fs.length();i++){JSONObject f=fs.getJSONObject(i);names.add(f.optString("name"));ids.add(f.optString("id"));if(preferred.equals(f.optString("id")))selected=i+1;}}catch(Exception ignored){}
        options(inputFridge,names);inputFridge.setSelection(selected);listen(inputFridge,()->{int index=inputFridge.getSelectedItemPosition();preferred=index>=0&&index<ids.size()?ids.get(index):"";});
        input=field(content,"按遥控器语音键输入","utterance",utterance);input.setSingleLine(false);input.getLayoutParams().height=dp(76);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        status=text("说完收起键盘，再选择冰箱、批次与数量。确认前不会改变库存。",14,0xffb7dccb);content.addView(status);
        LinearLayout actions=row(root);Button keyboard=button("语音 / 输入法");keyboard.setOnClickListener(v->showKeyboard());action(actions,keyboard);
        parse=button("解析并核对");parse.setOnClickListener(v->requestDraft());action(actions,parse);
        Button manual=button("手动填写");manual.setOnClickListener(v->{requestId++;busy=false;utterance=input.getText().toString().trim();try{confirmScreen(new JSONObject().put("food","").put("issues",new JSONArray()));}catch(Exception ignored){}});action(actions,manual);
        Button cancel=button("返回");cancel.setOnClickListener(v->finish());action(actions,cancel);input.requestFocus();
    }
    private void showKeyboard(){if(draft!=null||input==null)return;input.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);}
    private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);}
    public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&!shownKeyboard&&draft==null){shownKeyboard=true;input.post(()->showKeyboard());}}
    private void requestDraft(){
        if(busy)return;utterance=input.getText().toString().trim();if(utterance.isEmpty()){input.setError("请说出或输入库存变动");return;}hideKeyboard();busy=true;parse.setEnabled(false);inputFridge.setEnabled(false);status.setText("正在解析，请稍候…");final int id=++requestId;
        new Thread(()->{HttpURLConnection connection=null;try{
            ByteArrayOutputStream configBytes=new ByteArrayOutputStream();try(InputStream in=getAssets().open("schedule-service.json")){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1)configBytes.write(b,0,n);}
            JSONObject config=new JSONObject(configBytes.toString("UTF-8"));if(config.optString("url").isEmpty()||config.optString("token").isEmpty())throw new IOException("not configured");
            connection=(HttpURLConnection)new URL(config.getString("url")+"/fridge/parse").openConnection();connection.setConnectTimeout(4000);connection.setReadTimeout(15000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setRequestProperty("Authorization","Bearer "+config.getString("token"));
            try(OutputStream out=connection.getOutputStream()){out.write(new JSONObject().put("text",utterance).put("inventoryContext",inventoryContext()).toString().getBytes("UTF-8"));}if(connection.getResponseCode()!=200)throw new IOException("service unavailable");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){bytes.write(b,0,n);if(bytes.size()>32768)throw new IOException("response too large");}}
            final JSONObject result=new JSONObject(bytes.toString("UTF-8"));runOnUiThread(()->{if(isFinishing()||id!=requestId)return;busy=false;parse.setEnabled(true);inputFridge.setEnabled(true);if(result.optBoolean("unsupported")){status.setText("请把不同食材或不同操作分开说，一次只处理一种。");parse.requestFocus();}else confirmScreen(result);});
        }catch(Exception e){runOnUiThread(()->{if(isFinishing()||id!=requestId)return;busy=false;parse.setEnabled(true);inputFridge.setEnabled(true);status.setText("暂时无法连接解析服务，可以重试或手动填写。库存未改变。");parse.requestFocus();});}finally{if(connection!=null)connection.disconnect();}},"fridge-draft").start();
    }
    private JSONObject inventoryContext() throws Exception {
        JSONArray fs=InventoryStore.load(this).data.getJSONArray("fridges"),hints=new JSONArray();ArrayList<JSONObject> ordered=new ArrayList<>();
        for(int i=0;i<fs.length();i++){JSONObject f=fs.getJSONObject(i);if(f.optString("id").equals(preferred)||(!f.optString("name").isEmpty()&&utterance.contains(f.optString("name"))))ordered.add(0,f);else ordered.add(f);}
        int remaining=32;
        for(JSONObject f:ordered){if(hints.length()>=20)break;JSONArray items=f.optJSONArray("items"),foodHints=new JSONArray();if(items!=null)for(int i=0;i<items.length()&&remaining>0;i++){JSONObject t=items.getJSONObject(i),life=t.optJSONObject("shelfLife");foodHints.put(new JSONObject().put("name",t.optString("name")).put("quantity",t.optInt("quantity")).put("unit",t.optString("unit")).put("location",t.optString("location")).put("endDate",life==null?"":life.optString("endDate")));remaining--;}
            hints.put(new JSONObject().put("id",f.optString("id")).put("name",f.optString("name")).put("items",foodHints));}
        return new JSONObject().put("preferredFridgeId",preferred).put("fridges",hints);
    }
    private JSONObject currentFridge(){int index=fridgeChoice.getSelectedItemPosition()-1;return index>=0&&index<fridges.length()?fridges.optJSONObject(index):null;}
    private String currentAction(){return operation.getSelectedItemPosition()==1?"add":operation.getSelectedItemPosition()==2?"remove":"";}
    private String locationValue(){int i=location.getSelectedItemPosition();return i>=0&&i<locations.size()?locations.get(i):"";}
    private void setLocation(String name){int i=locations.indexOf(name);location.setSelection(Math.max(0,i));}
    private JSONObject currentItem(){JSONObject f=currentFridge();int pos=batch.getSelectedItemPosition();return f==null||pos<=0||pos>=batchIds.size()?null:InventoryStore.item(f,batchIds.get(pos));}
    private void confirmScreen(JSONObject d){
        try{snapshot=InventoryStore.load(this);fridges=snapshot.data.getJSONArray("fridges");}catch(Exception e){Toast.makeText(this,"库存读取失败，未作修改",Toast.LENGTH_LONG).show();return;}
        draft=d;hideKeyboard();showingExisting=false;autoLocation=d.optBoolean("locationInferred")?value(d,"location"):"";selectedBatch=d.optString("itemId");heading("核对这次库存变动","先选冰箱和食材批次，再核对数量；最后确认才会更新库存。");
        JSONArray issues=d.optJSONArray("issues");StringBuilder warnings=new StringBuilder();if(issues!=null)for(int i=0;i<issues.length();i++)warnings.append("• ").append(issues.optString(i)).append("  ");
        if(warnings.length()>0)content.addView(text(warnings.toString(),12,0xfff1ca91));
        LinearLayout top=row(content);fridgeChoice=choice(column(top),"选择对应冰箱","fridge");operation=choice(column(top),"本次操作","operation");
        ArrayList<String> names=new ArrayList<>();names.add("请选择冰箱");int selected=0;String matchedId=d.optBoolean("fridgeMatched")?d.optString("fridgeId"):"",desired=d.optString("fridgeId",preferred),spoken=d.optString("fridgeName");
        for(int i=0;i<fridges.length();i++){JSONObject f=fridges.optJSONObject(i);names.add(f.optString("name"));if((!matchedId.isEmpty()&&matchedId.equals(f.optString("id")))||(matchedId.isEmpty()&&((!spoken.isEmpty()&&spoken.equals(f.optString("name")))||(spoken.isEmpty()&&desired.equals(f.optString("id"))))))selected=i+1;}
        options(fridgeChoice,names);fridgeChoice.setSelection(selected);options(operation,Arrays.asList("请选择操作","放入 / 增加","吃掉 / 减少"));operation.setSelection(d.optString("action").equals("add")?1:d.optString("action").equals("remove")?2:0);
        batch=choice(content,"食材 / 批次（补货可选已有批次，也可新增）","batch");
        LinearLayout middle=row(content);food=field(column(middle),"食材名称","food",value(d,"food"));quantity=field(column(middle),"本次变动数量","quantity",value(d,"quantity"));quantity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);unit=field(column(middle),"单位（如个、盒、瓶）","unit",value(d,"unit"));
        food.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});unit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});quantity.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        LinearLayout bottom=row(content);location=choice(column(bottom),"存放分区","location");start=field(column(bottom),"生产 / 开始日期 · 可留空","startDate",value(d,"startDate"));end=field(column(bottom),"到期日期 · YYYY-MM-DD，可留空","endDate",value(d,"endDate"));
        locations.clear();locations.add("");for(String loc:new String[]{"上部冷藏","下部冷藏","冷藏","冷冻","其他"})locations.add(loc);
        for(int f=0;f<fridges.length();f++){JSONArray items=fridges.optJSONObject(f).optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++){String loc=items.optJSONObject(i).optString("location");if(!loc.isEmpty()&&!locations.contains(loc))locations.add(loc);}}
        options(location,locations);setLocation(value(d,"location"));newValues=new String[]{food.getText().toString(),unit.getText().toString(),locationValue(),start.getText().toString(),end.getText().toString()};
        recommendations=new LinearLayout(this);recommendations.setOrientation(LinearLayout.VERTICAL);content.addView(recommendations);
        stock=text("",12,0xffb7dccb);content.addView(stock);content.addView(text("原话："+utterance,11,MUTED));
        rebuildBatches();listen(fridgeChoice,()->{rebuildBatches();});listen(operation,()->rebuildBatches());listen(batch,()->displayBatch());
        LinearLayout actions=row(root);Button back=button("重新说 / 返回修改");back.setOnClickListener(v->{requestId++;inputScreen();});action(actions,back);Button preview=button("核对变更");preview.setOnClickListener(v->preview());action(actions,preview);Button cancel=button("取消");cancel.setOnClickListener(v->finish());action(actions,cancel);preview.requestFocus();
    }
    private void rebuildBatches(){
        String previous=selectedBatch;JSONObject f=currentFridge();boolean adding=currentAction().equals("add");
        ArrayList<String> labels=new ArrayList<>();labels.add("请选择食材 / 批次");batchIds.clear();batchIds.add("__CHOOSE__");
        if(adding){labels.add("作为新食材 / 新批次放入");batchIds.add("__NEW__");}
        int chosen=adding&&previous.equals("__NEW__")?1:0,match=0,matches=0,nameMatches=0;
        if(f!=null){JSONArray items=f.optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++){
            JSONObject t=items.optJSONObject(i);batchIds.add(t.optString("id"));JSONObject life=t.optJSONObject("shelfLife");
            labels.add(t.optString("name")+"  ×"+t.optInt("quantity")+t.optString("unit")+"  · "+t.optString("location")+" · "+(life==null?"日期未知":life.optString("endDate")));
            int index=labels.size()-1;if(previous.equals(t.optString("id")))chosen=index;
            if(t.optString("name").equals(draft.optString("food"))){
                nameMatches++;
                boolean sameLocation=draft.optString("location").isEmpty()||draft.optBoolean("locationInferred")||draft.optString("location").equals(t.optString("location"))||(draft.optString("location").equals("冷藏")&&t.optString("location").contains("冷藏"))||(draft.optString("location").equals("冷冻")&&t.optString("location").contains("冷冻"));
                boolean sameUnit=draft.optString("unit").isEmpty()||draft.optBoolean("unitInferred")||draft.optString("unit").equals(t.optString("unit"));
                boolean sameLife=!adding||draft.optString("endDate").isEmpty()||(life!=null&&draft.optString("endDate").equals(life.optString("endDate"))&&(draft.optString("startDate").isEmpty()||draft.optString("startDate").equals(life.optString("startDate"))));
                if(sameLocation&&sameUnit&&sameLife){match=index;matches++;}
            }
        }}
        boolean explicitlyNew=draft.optBoolean("createNewBatch")||(draft.optBoolean("shelfLifeMentioned")&&draft.optString("endDate").isEmpty());
        if(chosen==0){
            if(adding&&explicitlyNew)chosen=1;
            else if(!currentAction().isEmpty()&&matches==1)chosen=match;
            else if(adding&&((nameMatches==0&&(draft.optJSONArray("foodCandidates")==null||draft.optJSONArray("foodCandidates").length()<2))||(!draft.optString("endDate").isEmpty()||!draft.optString("location").isEmpty()&&!draft.optBoolean("locationInferred"))&&matches==0))chosen=1;
        }
        options(batch,labels);batch.setSelection(chosen);displayBatch();
    }
    private boolean newBatchSelected(){return currentAction().equals("add")&&batch.getSelectedItemPosition()==1;}
    private void displayBatch(){
        JSONObject t=currentItem();boolean existing=t!=null;
        if(existing){if(!showingExisting)newValues=new String[]{food.getText().toString(),unit.getText().toString(),locationValue(),start.getText().toString(),end.getText().toString()};food.setText(t.optString("name"));unit.setText(t.optString("unit"));setLocation(t.optString("location"));JSONObject life=t.optJSONObject("shelfLife");start.setText(life==null?"":life.optString("startDate"));end.setText(life==null?"":life.optString("endDate"));selectedBatch=t.optString("id");stock.setText("当前库存："+t.optInt("quantity")+t.optString("unit")+"。已有批次的分区、单位和保质期保持原值。"+(!draft.optString("unit").isEmpty()&&!draft.optString("unit").equals(t.optString("unit"))?" 注意：原话单位与本批次不同，请按本批次单位核对数量。":""));}
        else {if(showingExisting&&newValues!=null){food.setText(newValues[0]);unit.setText(newValues[1]);setLocation(newValues[2]);start.setText(newValues[3]);end.setText(newValues[4]);}selectedBatch=newBatchSelected()?"__NEW__":"__CHOOSE__";String hint=suggestLocation();stock.setText(currentAction().equals("remove")?"请选中一批已有食材；不会模糊匹配后直接扣减。":newBatchSelected()?hint+"新增批次；保质期不清楚可以留空。":hint+"请选中已有批次；需要独立保存时选择「新食材 / 新批次」。");}
        showingExisting=existing;for(View v:new View[]{food,unit,location,start,end})v.setEnabled(!existing);
        renderSuggestions(existing);
    }
    private void renderSuggestions(boolean existing){
        recommendations.removeAllViews();JSONObject f=currentFridge();if(f==null||existing)return;
        if(!draft.optString("location").isEmpty()&&!draft.optBoolean("locationInferred")){recommendations.addView(text("原话指定分区："+draft.optString("location"),12,0xffb7dccb));return;}
        JSONObject all=draft.optJSONObject("locationSuggestionsByFridge");JSONArray suggestions=all==null?null:all.optJSONArray(f.optString("id"));
        if(suggestions==null||suggestions.length()==0)return;
        recommendations.addView(text("分区建议 · 相似匹配度，供你核对",12,0xffb7dccb));LinearLayout choices=row(recommendations);
        for(int i=0;i<Math.min(3,suggestions.length());i++){JSONObject suggestion=suggestions.optJSONObject(i);if(suggestion==null)continue;String loc=suggestion.optString("location");LinearLayout col=column(choices);
            Button adopt=button(loc+" · "+suggestion.optInt("score")+"%");adopt.setTextSize(13);adopt.setTag("suggestion:"+loc);adopt.setOnClickListener(v->{setLocation(loc);autoLocation="";Toast.makeText(this,"已选「"+loc+"」，请继续核对",Toast.LENGTH_SHORT).show();});col.addView(adopt,new LinearLayout.LayoutParams(-1,dp(36)));
            JSONArray evidence=suggestion.optJSONArray("evidence");StringBuilder detail=new StringBuilder("参考：");if(evidence!=null)for(int j=0;j<evidence.length();j++){if(j>0)detail.append("、");detail.append(evidence.optString(j));}col.addView(text(detail.toString(),10,MUTED));}
    }
    private String suggestLocation(){
        JSONObject f=currentFridge();String name=food.getText().toString().trim(),current=locationValue();
        if((!draft.optString("location").isEmpty()&&!draft.optBoolean("locationInferred"))||(!current.isEmpty()&&!current.equals(autoLocation)))return "";
        HashSet<String> matches=new HashSet<>();HashSet<String> units=new HashSet<>();
        if(f!=null){JSONArray items=f.optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++){JSONObject t=items.optJSONObject(i);if(name.equals(t.optString("name"))){if(!t.optString("location").isEmpty())matches.add(t.optString("location"));units.add(t.optString("unit"));}}}
        autoLocation=matches.size()==1?matches.iterator().next():"";setLocation(autoLocation);
        String hint=autoLocation.isEmpty()?"":"参考同名食材填入「"+autoLocation+"」，请核对。";
        String u=unit.getText().toString().trim();if(!units.isEmpty()&&!u.isEmpty()&&!units.contains(u))hint+="原有记录单位不同，本次按「"+u+"」保存，请核对。";
        return hint;
    }
    private void error(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void preview(){
        hideKeyboard();if(saved)return;JSONObject f=currentFridge(),t=currentItem();String action=currentAction(),name=food.getText().toString().trim(),count=quantity.getText().toString().trim(),u=unit.getText().toString().trim(),loc=locationValue(),s=start.getText().toString().trim(),e=end.getText().toString().trim();
        if(f==null){error("请选择对应冰箱");fridgeChoice.requestFocus();return;}if(action.isEmpty()){error("请选择放入还是吃掉");operation.requestFocus();return;}
        int delta;try{delta=Integer.parseInt(count);}catch(Exception ex){delta=0;}if(delta<1||delta>9999){quantity.setError("请输入 1 到 9999 的整数");quantity.requestFocus();return;}
        if(action.equals("remove")&&t==null){error("请选择要扣减的食材批次");batch.requestFocus();return;}
        if(action.equals("add")&&t==null&&!newBatchSelected()){error("请选中已有批次，或选择新增批次");batch.requestFocus();return;}
        if(name.isEmpty()||u.isEmpty()||loc.isEmpty()){error("请补全食材名称、单位和分区");return;}
        String lifeError=InventoryStore.dateError(s,e);if(lifeError!=null){error(lifeError);return;}
        int before=t==null?0:t.optInt("quantity"),after=action.equals("remove")?before-delta:before+delta;if(after<0){error("库存不足，当前只有 "+before+u);return;}if(after>999999){error("库存数量过大，请核对");return;}
        final int amount=delta;final String fridgeId=f.optString("id"),itemId=t==null?"":t.optString("id");
        String summary=f.optString("name")+" · "+loc+"\n"+name+"\n"+(action.equals("add")?"放入 ":"吃掉 / 减少 ")+delta+u+"\n库存："+before+u+" → "+after+u+(after==0?"（用完后移出清单）":"")+"\n"+(e.isEmpty()?"保质期未知 · 尽快食用":("到期："+e))+(t==null?"\n作为独立新批次保存":"\n更新所选批次，保留原保质期");
        if(!e.isEmpty()&&ScheduleStore.at(e,"23:59")<System.currentTimeMillis())summary+="\n注意：这批食材已经过期";
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("确认这次库存变更？").setMessage(summary).setNegativeButton("返回修改",null).setPositiveButton("确认更新",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{if(saved)return;try{InventoryStore.apply(this,snapshot.revision,fridgeId,itemId,action,amount,name,u,loc,s,e);saved=true;dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);Toast.makeText(this,"库存已更新",Toast.LENGTH_LONG).show();setResult(RESULT_OK);dialog.dismiss();finish();}catch(IllegalStateException ex){dialog.dismiss();error(ex.getMessage());confirmScreen(captureDraft());}catch(Exception ex){error(ex instanceof IllegalArgumentException?ex.getMessage():"更新失败，库存未确认保存，请重试");}}));dialog.show();
    }
    private JSONObject captureDraft(){try{JSONObject d=new JSONObject(draft.toString());d.put("food",food.getText().toString());d.put("quantity",quantity.getText().toString());d.put("unit",unit.getText().toString());d.put("location",locationValue());d.put("startDate",start.getText().toString());d.put("endDate",end.getText().toString());d.put("action",currentAction());JSONObject f=currentFridge();d.put("fridgeId",f==null?"":f.optString("id"));d.put("fridgeName","");d.put("itemId",selectedBatch);return d;}catch(Exception e){return draft;}}
    protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("preferred",preferred);state.putString("utterance",draft==null?input.getText().toString():utterance);if(draft!=null)state.putString("draft",captureDraft().toString());}
    protected void onDestroy(){requestId++;super.onDestroy();}
    public void onBackPressed(){if(draft!=null)inputScreen();else if(busy){requestId++;busy=false;parse.setEnabled(true);inputFridge.setEnabled(true);status.setText("已取消解析，库存未改变");}else super.onBackPressed();}
}
