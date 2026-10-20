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
import java.util.UUID;

/** Foreground IME input, editable draft, explicit local save. */
public class VoiceScheduleActivity extends Activity {
    private LinearLayout root,content;
    private EditText input,title,date,time,depart;
    private CheckBox must;
    private TextView status;
    private Button parse;
    private boolean shownKeyboard,busy,saved;
    private int requestId;
    private String utterance="";
    private JSONObject draft;
    private static final int WHITE=0xffedf0f6,MUTED=0xffaeb8cc;

    public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(44),dp(22),dp(44),dp(18));root.setBackgroundColor(0xff151923);
        setContentView(root);
        if(state!=null)utterance=state.getString("utterance","");
        inputScreen();
        if(state!=null&&state.containsKey("draft")){try{confirmScreen(new JSONObject(state.getString("draft")));}catch(Exception ignored){}}
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private void heading(String title,String hint){root.removeAllViews();TextView h=text(title,26,WHITE);h.setTypeface(null,Typeface.BOLD);root.addView(h);TextView sub=text(hint,14,MUTED);sub.setPadding(0,dp(6),0,dp(12));root.addView(sub);ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(WHITE);b.setTextSize(16);b.setAllCaps(false);StateListDrawable states=new StateListDrawable();for(boolean focus:new boolean[]{true,false}){GradientDrawable d=new GradientDrawable();d.setColor(focus?0xff4c4768:0xff2a3243);d.setCornerRadius(dp(9));d.setStroke(dp(2),focus?0xffd5c6ff:0xff3c465b);states.addState(focus?new int[]{android.R.attr.state_focused}:new int[]{},d);}b.setBackground(states);return b;}
    private EditText field(LinearLayout parent,String label,String value){parent.addView(text(label,12,MUTED));EditText e=new EditText(this);e.setText(value);e.setTextColor(WHITE);e.setTextSize(18);e.setSingleLine(true);e.setPadding(dp(10),dp(5),dp(10),dp(5));e.setBackgroundColor(0xff252d3d);e.setSelectAllOnFocus(true);e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(42));p.bottomMargin=dp(9);parent.addView(e,p);return e;}
    private void action(LinearLayout row,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(3),dp(6),dp(3),0);row.addView(b,p);}
    private LinearLayout actions(){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);root.addView(row);return row;}
    private void inputScreen(){
        draft=null;busy=false;shownKeyboard=false;
        heading("说一句，记住重要的事","按遥控器语音键输入，识别完成后选择「解析并核对」。");
        input=field(content,"例如：明天下午四点十分接孩子，下午四点出发",utterance);input.setSingleLine(false);input.setMinLines(2);input.getLayoutParams().height=dp(80);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setHint("请说出日期、时间和要做的事");input.setHintTextColor(MUTED);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        status=text("说完后收起键盘，再核对事项和时间；确认前不会保存。",14,0xffb9d7ce);content.addView(status);
        LinearLayout row=actions();Button keyboard=button("语音 / 输入法");keyboard.setOnClickListener(v->showKeyboard());action(row,keyboard);
        parse=button("解析并核对");parse.setOnClickListener(v->requestDraft());action(row,parse);
        Button manual=button("手动填写");manual.setOnClickListener(v->{utterance=input.getText().toString().trim();hideKeyboard();try{JSONObject d=new JSONObject();d.put("title",utterance);d.put("date",ScheduleStore.today());d.put("issues",new JSONArray().put("请填写并核对日期和时间"));confirmScreen(d);}catch(Exception ignored){}});action(row,manual);
        Button cancel=button("返回");cancel.setOnClickListener(v->finish());action(row,cancel);input.requestFocus();
    }
    private void showKeyboard(){if(input==null||draft!=null)return;input.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);}
    private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);}
    public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&!shownKeyboard&&draft==null){shownKeyboard=true;input.post(()->showKeyboard());}}
    private JSONObject configuration() throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=getAssets().open("schedule-service.json")){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}
    private void requestDraft(){
        if(busy)return;utterance=input.getText().toString().trim();if(utterance.isEmpty()){input.setError("请先说出或输入事项");return;}
        hideKeyboard();busy=true;parse.setEnabled(false);status.setText("正在解析，请稍候…");final int id=++requestId;
        new Thread(()->{HttpURLConnection connection=null;try{
            JSONObject config=configuration();String url=config.optString("url"),token=config.optString("token");if(url.isEmpty()||token.isEmpty())throw new IOException("not configured");
            connection=(HttpURLConnection)new URL(url+"/parse").openConnection();connection.setConnectTimeout(4000);connection.setReadTimeout(15000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setRequestProperty("Authorization","Bearer "+token);
            byte[] body=new JSONObject().put("text",utterance).toString().getBytes("UTF-8");try(OutputStream out=connection.getOutputStream()){out.write(body);}if(connection.getResponseCode()!=200)throw new IOException("service unavailable");
            ByteArrayOutputStream response=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){response.write(b,0,n);if(response.size()>32768)throw new IOException("response too large");}}
            final JSONObject result=new JSONObject(response.toString("UTF-8"));runOnUiThread(()->{if(isFinishing()||id!=requestId)return;busy=false;confirmScreen(result);});
        }catch(Exception e){String message=e.getMessage();final String reason=(message!=null&&message.toLowerCase(java.util.Locale.US).contains("cleartext"))?"局域网连接被系统拦截":e instanceof java.net.SocketTimeoutException?"连接超时":e instanceof java.io.FileNotFoundException?"找不到服务配置":e instanceof org.json.JSONException?"服务数据格式异常":e instanceof java.net.ConnectException?"服务暂时不可达":"服务连接失败";android.util.Log.w("FlipClockSchedule",reason+" ("+e.getClass().getSimpleName()+")");runOnUiThread(()->{if(isFinishing()||id!=requestId)return;busy=false;parse.setEnabled(true);status.setText(reason+"。可以重试，或选择「手动填写」。内容尚未保存。");parse.requestFocus();});}finally{if(connection!=null)connection.disconnect();}},"schedule-draft").start();
    }
    private String value(JSONObject d,String key){return d.isNull(key)?"":d.optString(key);}
    private void confirmScreen(JSONObject d){
        draft=d;hideKeyboard();heading("核对这条日程","请检查日期和时间；只有选择「确认保存」才会添加提醒。");
        JSONArray issues=d.optJSONArray("issues");StringBuilder hint=new StringBuilder();if(issues!=null)for(int i=0;i<issues.length();i++)hint.append("• ").append(issues.optString(i)).append("  ");
        if(hint.length()>0){TextView warnings=text(hint.toString(),13,0xfff2ca91);warnings.setPadding(0,0,0,dp(7));content.addView(warnings);}
        title=field(content,"事项",value(d,"title"));title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        LinearLayout columns=new LinearLayout(this);content.addView(columns);LinearLayout a=new LinearLayout(this),b=new LinearLayout(this),c=new LinearLayout(this);for(LinearLayout col:new LinearLayout[]{a,b,c}){col.setOrientation(LinearLayout.VERTICAL);col.setPadding(0,0,dp(12),0);columns.addView(col,new LinearLayout.LayoutParams(0,-2,1));}
        date=field(a,"日期 · YYYY-MM-DD",value(d,"date"));time=field(b,"计划时间 · HH:mm",value(d,"time"));depart=field(c,"出发时间 · 可留空",value(d,"departureTime"));
        must=new CheckBox(this);must.setText("这一天内必须完成");must.setTextColor(WHITE);must.setChecked(d.optBoolean("mustFinishToday"));content.addView(must);
        TextView source=text("原话："+utterance,12,MUTED);content.addView(source);
        LinearLayout row=actions();Button again=button("重新说 / 返回修改");again.setOnClickListener(v->{requestId++;inputScreen();});action(row,again);
        Button save=button("确认保存");save.setOnClickListener(v->validateAndSave(save));action(row,save);Button cancel=button("取消");cancel.setOnClickListener(v->finish());action(row,cancel);save.requestFocus();
    }
    private void validateAndSave(Button save){
        if(saved)return;String name=title.getText().toString().trim(),day=date.getText().toString().trim(),start=time.getText().toString().trim(),leave=depart.getText().toString().trim();
        if(name.isEmpty()){title.setError("请输入事项");title.requestFocus();return;}
        if(!day.matches("\\d{4}-\\d{2}-\\d{2}")||!start.matches("\\d{2}:\\d{2}")||ScheduleStore.at(day,start)<0){time.setError("请填写有效日期和时间");time.requestFocus();return;}
        if(!leave.isEmpty()&&(!leave.matches("\\d{2}:\\d{2}")||ScheduleStore.at(day,leave)<0||leave.compareTo(start)>0)){depart.setError("出发时间应早于计划时间");depart.requestFocus();return;}
        if(ScheduleStore.at(day,start)<=System.currentTimeMillis()){new AlertDialog.Builder(this).setTitle("计划时间已经过去").setMessage("请修改日期或时间，再保存提醒。").setPositiveButton("返回修改",(x,y)->date.requestFocus()).show();return;}
        try{JSONObject task=new JSONObject();task.put("id",UUID.randomUUID().toString());task.put("title",name);task.put("date",day);task.put("time",start);task.put("departureTime",leave);task.put("mustFinishToday",must.isChecked());task.put("completed",false);task.put("note","");JSONArray tasks=ScheduleStore.load(this);tasks.put(task);ScheduleStore.save(this,tasks);saved=true;save.setEnabled(false);Toast.makeText(this,"已保存日程",Toast.LENGTH_LONG).show();setResult(RESULT_OK);finish();}catch(Exception e){Toast.makeText(this,"保存失败，请重试",Toast.LENGTH_LONG).show();}
    }
    protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("utterance",draft==null?input.getText().toString():utterance);if(draft!=null)try{draft.put("title",title.getText().toString());draft.put("date",date.getText().toString());draft.put("time",time.getText().toString());draft.put("departureTime",depart.getText().toString());draft.put("mustFinishToday",must.isChecked());state.putString("draft",draft.toString());}catch(Exception ignored){}}
    protected void onDestroy(){requestId++;super.onDestroy();}
    public void onBackPressed(){if(draft!=null){inputScreen();}else if(busy){requestId++;busy=false;parse.setEnabled(true);status.setText("已取消解析，内容未保存");}else super.onBackPressed();}
}
