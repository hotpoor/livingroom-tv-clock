package cn.livingroom.flipclock;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

public class SchedulePanel extends LinearLayout {
    private final Activity activity;
    private JSONArray tasks;
    private final LinearLayout body;
    private final Handler handler=new Handler();
    private final ArrayList<Runnable> updates=new ArrayList<>();
    private final Runnable ticker=new Runnable(){public void run(){for(Runnable r:updates)r.run();handler.postDelayed(this,1000);}};
    private View initial;
    private static final int WHITE=0xffeef0f5,MUTED=0xff9299aa,PURPLE=0xffc8c0f2,AMBER=0xffedc28d;
    SchedulePanel(Activity a){
        super(a);activity=a;tasks=ScheduleStore.load(a);ScheduleStore.schedule(a,tasks);
        setOrientation(VERTICAL);setPadding(dp(18),dp(22),dp(18),dp(16));setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff201e2b,0xff12151c}));
        addView(label("把今天重要的事，放在眼前",11,MUTED));
        LinearLayout heading=row();TextView title=label("备忘与日程",25,WHITE);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new LayoutParams(0,-2,1));
        Button add=button("＋");add.setContentDescription("添加日程");add.setOnClickListener(v->new AlertDialog.Builder(activity).setTitle("添加日程").setItems(new String[]{"语音 / 自然语言输入","手动填写日期时间"},(d,w)->{if(w==0)activity.startActivity(new Intent(activity,VoiceScheduleActivity.class));else edit(-1);}).setNegativeButton("取消",null).show());heading.addView(add,new LayoutParams(dp(36),dp(34)));initial=add;
        LayoutParams hp=new LayoutParams(-1,-2);hp.topMargin=dp(7);hp.bottomMargin=dp(12);addView(heading,hp);
        TextView date=label(ScheduleStore.today().replace('-','.')+"  ·  上海时间",12,MUTED);addView(date);
        ScrollView scroll=new ScrollView(a);scroll.setVerticalScrollBarEnabled(false);body=new LinearLayout(a);body.setOrientation(VERTICAL);body.setPadding(0,dp(18),0,dp(8));scroll.addView(body);addView(scroll,new LayoutParams(-1,0,1));
        addView(label("↑ ↓ 浏览    确认管理事项    返回收起",10,MUTED));rebuild();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setOrientation(HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String text,int size,int color){TextView t=new TextView(activity);t.setText(text);t.setTextSize(size);t.setTextColor(color);return t;}
    private GradientDrawable shape(int color,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(11));d.setStroke(dp(1),border);return d;}
    private StateListDrawable surface(){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_focused},shape(0xff373345,0xffc8c0f2));d.addState(new int[]{},shape(0xff252633,0xff3b3c4c));return d;}
    private Button button(String s){Button b=new Button(activity);b.setText(s);b.setTextColor(WHITE);b.setTextSize(14);b.setAllCaps(false);b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setBackground(surface());return b;}
    protected void onAttachedToWindow(){super.onAttachedToWindow();handler.post(ticker);}
    protected void onDetachedFromWindow(){handler.removeCallbacksAndMessages(null);super.onDetachedFromWindow();}
    public void focusFirst(){post(()->initial.requestFocus());}
    public void refresh(){tasks=ScheduleStore.load(activity);rebuild();}
    private void rebuild(){
        updates.clear();body.removeAllViews();
        if(tasks.length()==0){TextView empty=label("今天想记住什么？\n点击右上角 ＋ 添加事项。",17,MUTED);empty.setLineSpacing(dp(10),1);body.addView(empty);return;}
        for(int i=0;i<tasks.length();i++)card(i,tasks.optJSONObject(i));
    }
    private void card(int index,JSONObject t){
        boolean done=t.optBoolean("completed"),urgent=t.optBoolean("mustFinishToday");
        LinearLayout card=new LinearLayout(activity);card.setOrientation(VERTICAL);card.setPadding(dp(15),dp(14),dp(15),dp(14));card.setBackground(surface());card.setFocusable(true);card.setClickable(true);card.setOnClickListener(v->manage(index));
        LinearLayout top=row();String kind=t.optString("departureTime").isEmpty()?urgent?"今日必办":"日程":"接送提醒";
        top.addView(label(urgent?"今天一定要完成":kind,11,urgent?AMBER:PURPLE),new LayoutParams(0,-2,1));top.addView(label(done?"✓ 已完成":t.optString("time"),13,done?0xff9acbb5:WHITE));card.addView(top);
        TextView title=label(t.optString("title"),19,done?MUTED:WHITE);title.setTypeface(null,Typeface.BOLD);LayoutParams tp=new LayoutParams(-1,-2);tp.topMargin=dp(10);tp.bottomMargin=dp(12);card.addView(title,tp);
        TextView caption=label("",11,MUTED),count=label("",22,urgent?AMBER:PURPLE);count.setTypeface(null,Typeface.BOLD);card.addView(caption);card.addView(count);
        String details=t.optString("date")+(t.optString("departureTime").isEmpty()?"":"  "+t.optString("departureTime")+" 出发")+"  ·  "+t.optString("time")+(kind.equals("接送提醒")?" 接送":" 开始");
        TextView info=label(details,10,MUTED);LayoutParams ip=new LayoutParams(-1,-2);ip.topMargin=dp(10);card.addView(info,ip);
        TextView deadline=label("",12,AMBER);LayoutParams dp=new LayoutParams(-1,-2);dp.topMargin=dp(9);card.addView(deadline,dp);
        String note=t.optString("note");if(!note.isEmpty()&&!note.equals("今天一定要完成")&&!note.equals("提前出发，"+t.optString("time")+" 接放学")){TextView n=label(note,12,urgent?AMBER:MUTED);LayoutParams np=new LayoutParams(-1,-2);np.topMargin=dp(8);card.addView(n,np);}
        Runnable update=()->{
            long now=System.currentTimeMillis(),start=ScheduleStore.at(t.optString("date"),t.optString("time"));
            long departure=t.optString("departureTime").isEmpty()?-1:ScheduleStore.at(t.optString("date"),t.optString("departureTime"));
            if(done){caption.setText("这一件事已经完成");count.setText("完成啦");deadline.setVisibility(GONE);return;}
            if(departure>now){caption.setText("距离出发还有");count.setText(ScheduleStore.countdown(departure-now));}
            else if(start>now){caption.setText(departure>0?"该出发了 · 距离接送还有":"距离开始还有");count.setText(ScheduleStore.countdown(start-now));}
            else{caption.setText(departure>0?"接送时间已到 · 请确认完成":urgent?"已到开始时间 · 今天一定要完成":"已到计划时间 · 尚未标记完成");count.setText("已过 "+ScheduleStore.countdown(now-start));}
            if(urgent){long end=ScheduleStore.deadline(t);deadline.setText(end>now?"距当天结束 "+ScheduleStore.countdown(end-now):"当天已结束 · 事项仍未完成");}else deadline.setVisibility(GONE);
        };updates.add(update);update.run();
        LayoutParams cp=new LayoutParams(-1,-2);cp.bottomMargin=dp(14);body.addView(card,cp);if(index==0)initial=card;
    }
    private void manage(int index){JSONObject t=tasks.optJSONObject(index);new AlertDialog.Builder(activity).setTitle(t.optString("title")).setItems(new String[]{t.optBoolean("completed")?"恢复为待办":"标记已完成","编辑事项","删除事项"},(d,w)->{
        if(w==0){try{t.put("completed",!t.optBoolean("completed"));}catch(JSONException ignored){}ScheduleStore.save(activity,tasks);rebuild();focusFirst();}
        if(w==1)edit(index);
        if(w==2)new AlertDialog.Builder(activity).setTitle("删除这条事项？").setMessage(t.optString("title")).setNegativeButton("取消",null).setPositiveButton("删除",(x,y)->{JSONArray removed=new JSONArray();removed.put(t);try{t.put("completed",true);}catch(JSONException ignored){}ScheduleStore.schedule(activity,removed);tasks.remove(index);ScheduleStore.save(activity,tasks);rebuild();focusFirst();}).show();
    }).setNegativeButton("取消",null).show();}
    private EditText field(LinearLayout form,String label,String value){TextView l=label(label,12,MUTED);form.addView(l);EditText e=new EditText(activity);e.setSingleLine(true);e.setText(value);e.setTextSize(16);form.addView(e,new LayoutParams(-1,-2));return e;}
    private void edit(int index){
        JSONObject old=index<0?new JSONObject():tasks.optJSONObject(index);ScrollView scroll=new ScrollView(activity);LinearLayout form=new LinearLayout(activity);form.setOrientation(VERTICAL);form.setPadding(dp(24),dp(8),dp(24),dp(8));scroll.addView(form);
        EditText title=field(form,"事项",old.optString("title"));title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        EditText date=field(form,"日期 · yyyy-MM-dd",old.optString("date",ScheduleStore.today()));
        EditText time=field(form,"计划时间 · HH:mm",old.optString("time","17:00"));
        EditText depart=field(form,"出发时间 · HH:mm，可留空",old.optString("departureTime"));
        EditText note=field(form,"备注",old.optString("note"));
        CheckBox today=new CheckBox(activity);today.setText("这一天内必须完成");today.setChecked(old.optBoolean("mustFinishToday"));form.addView(today);
        AlertDialog d=new AlertDialog.Builder(activity).setTitle(index<0?"添加备忘与日程":"编辑事项").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
            String name=title.getText().toString().trim(),day=date.getText().toString().trim(),start=time.getText().toString().trim(),leave=depart.getText().toString().trim();
            if(name.isEmpty()){title.setError("请输入事项");return;}
            if(!day.matches("\\d{4}-\\d{2}-\\d{2}")||!start.matches("\\d{2}:\\d{2}")||ScheduleStore.at(day,start)<0){time.setError("请填写有效的日期和时间");return;}
            if(!leave.isEmpty()&&(!leave.matches("\\d{2}:\\d{2}")||ScheduleStore.at(day,leave)<0||ScheduleStore.at(day,leave)>ScheduleStore.at(day,start))){depart.setError("出发时间应早于计划时间");return;}
            try{JSONObject t=new JSONObject(old.toString());t.put("id",old.optString("id",UUID.randomUUID().toString()));t.put("title",name);t.put("date",day);t.put("time",start);t.put("departureTime",leave);t.put("note",note.getText().toString().trim());t.put("mustFinishToday",today.isChecked());if(index<0){t.put("completed",false);tasks.put(t);}else tasks.put(index,t);}catch(JSONException ignored){}
            ScheduleStore.save(activity,tasks);d.dismiss();rebuild();focusFirst();
        }));d.show();
    }
}
