package cn.livingroom.flipclock;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class FridgePanel extends LinearLayout {
    private final Activity activity;
    private final SharedPreferences prefs;
    private JSONObject inventory;
    private JSONArray fridges;
    private int selected;
    private final LinearLayout tabs,body;
    private final HorizontalScrollView scroll;
    private final ScrollView content;
    private final TextView summary;
    private final Runnable close;
    private boolean rebuilding,ready;
    private static final int WHITE=0xffe9edf1,MUTED=0xff87939d,MINT=0xffa7d9c8,AMBER=0xffe7bd7b;
    FridgePanel(Activity a,Runnable onClose) {
        super(a);activity=a;close=onClose;prefs=a.getSharedPreferences("fridges",0);
        setOrientation(VERTICAL);setPadding(dp(18),dp(22),dp(18),dp(16));
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff141d22,0xff101518}));
        load();
        addView(label("家庭食材  /  本机清单",11,MUTED));
        LinearLayout heading=row();TextView title=label("冰箱库存",25,WHITE);title.setTypeface(null,Typeface.BOLD);
        heading.addView(title,new LayoutParams(0,-2,1));
        Button manage=button("管理",false);manage.setTextSize(12);manage.setOnClickListener(v->manage());heading.addView(manage,new LayoutParams(dp(54),dp(34)));margin(heading,5,13);
        scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);
        tabs=row();scroll.addView(tabs);addView(scroll,new LayoutParams(-1,dp(43)));
        summary=label("",12,MUTED);margin(summary,12,10);
        View line=new View(a);line.setBackgroundColor(0xff2a343b);addView(line,new LayoutParams(-1,dp(1)));
        content=new ScrollView(a);content.setVerticalScrollBarEnabled(false);content.setFillViewport(false);
        body=new LinearLayout(a);body.setOrientation(VERTICAL);body.setPadding(0,dp(9),0,dp(6));content.addView(body);
        addView(content,new LayoutParams(-1,0,1));
        TextView help=label("← → 切换    ↑ ↓ 浏览    返回收起",10,MUTED);margin(help,8,0);
        rebuild(false);
    }
    private void load(){
        try {
            String stored=prefs.getString("inventory",null);
            if(stored==null){
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=activity.getAssets().open("inventory.json")){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
                inventory=new JSONObject(out.toString("UTF-8"));fridges=inventory.getJSONArray("fridges");selected=0;
                prefs.edit().putString("legacyTabsBackup",prefs.getString("tabs","[]")).apply();save();
            }else{inventory=new JSONObject(stored);fridges=inventory.getJSONArray("fridges");selected=Math.max(0,Math.min(prefs.getInt("selected",0),fridges.length()-1));}
        }catch(Exception e){inventory=new JSONObject();fridges=new JSONArray();Toast.makeText(activity,"库存读取失败，请稍后重试",Toast.LENGTH_LONG).show();}
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setOrientation(HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String text,int size,int color){TextView t=new TextView(activity);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");return t;}
    private void margin(View v,int top,int bottom){LayoutParams p=new LayoutParams(-1,-2);p.topMargin=dp(top);p.bottomMargin=dp(bottom);addView(v,p);}
    private GradientDrawable shape(int color,int stroke){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(9));if(stroke!=0)bg.setStroke(dp(1),stroke);return bg;}
    private StateListDrawable surface(boolean active){StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},shape(0xff314b4c,0xffb9e7d6));s.addState(new int[]{android.R.attr.state_pressed},shape(0xff314b4c,MINT));s.addState(new int[]{},shape(active?0xff29433f:0xff202b32,active?0xff719e90:0xff34434b));return s;}
    private Button button(String text,boolean active){Button b=new Button(activity);b.setText(text);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(active?MINT:WHITE);b.setPadding(dp(10),0,dp(10),0);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setBackground(surface(active));return b;}
    private JSONObject fridge(int i){return fridges.optJSONObject(i);}
    private String name(int i){JSONObject f=fridge(i);return f==null?"":f.optString("name");}
    private void save(){try{inventory.put("fridges",fridges);}catch(JSONException ignored){}prefs.edit().putString("inventory",inventory.toString()).putInt("selected",selected).apply();}
    private void rebuild(boolean focus){
        rebuilding=true;tabs.removeAllViews();
        for(int i=0;i<fridges.length();i++){
            final int index=i;Button b=button(name(i),i==selected);b.setSingleLine(true);b.setEllipsize(TextUtils.TruncateAt.END);b.setMaxWidth(dp(155));
            LayoutParams p=new LayoutParams(-2,dp(38));p.rightMargin=dp(7);tabs.addView(b,p);
            b.setOnFocusChangeListener((v,on)->{if(on&&!rebuilding&&ready){selected=index;save();refresh();scroll.post(()->scroll.smoothScrollTo(Math.max(0,b.getLeft()-dp(10)),0));}});
            b.setOnClickListener(v->{selected=index;save();refresh();});
        }
        Button plus=button("＋",false);tabs.addView(plus,new LayoutParams(dp(36),dp(38)));plus.setContentDescription("添加冰箱");plus.setOnClickListener(v->edit(false));
        rebuilding=false;refresh();if(focus)focusTabs();
    }
    private void refresh(){
        for(int i=0;i<fridges.length();i++){Button b=(Button)tabs.getChildAt(i);b.setBackground(surface(i==selected));b.setTextColor(i==selected?MINT:WHITE);}
        body.removeAllViews();content.scrollTo(0,0);
        if(fridges.length()==0){summary.setText("尚未添加冰箱");empty("添加你的第一台冰箱","点击顶部 ＋，建立独立清单。");return;}
        JSONObject f=fridge(selected);JSONArray items=f.optJSONArray("items");if(items==null)items=new JSONArray();
        int quantity=0;for(int i=0;i<items.length();i++)quantity+=items.optJSONObject(i).optInt("quantity");
        summary.setText(items.length()==0?name(selected)+"  ·  待录入":items.length()+" 种食材  ·  共 "+quantity+" 件  ·  已部分录入");
        if(items.length()==0){empty("食材清单待录入","已建好「"+name(selected)+"」\n录入后按冷藏、冷冻分区显示。");return;}
        LinkedHashMap<String,ArrayList<JSONObject>> groups=new LinkedHashMap<>();
        for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);String loc=item.optString("location","其他");if(!groups.containsKey(loc))groups.put(loc,new ArrayList<>());groups.get(loc).add(item);}
        for(Map.Entry<String,ArrayList<JSONObject>> entry:groups.entrySet()){
            LinearLayout header=row();TextView section=label((entry.getKey().contains("冷冻")?"❄  ":"▤  ")+entry.getKey(),12,MINT);header.addView(section,new LayoutParams(0,-2,1));header.addView(label(entry.getValue().size()+" 种",11,MUTED));
            LayoutParams hp=new LayoutParams(-1,-2);hp.topMargin=dp(7);hp.bottomMargin=dp(7);body.addView(header,hp);
            for(JSONObject item:entry.getValue())card(item);
        }
    }
    private void empty(String heading,String detail){
        LinearLayout box=new LinearLayout(activity);box.setOrientation(VERTICAL);box.setPadding(dp(16),dp(24),dp(16),dp(24));box.setBackground(shape(0xff1b272e,0xff304149));
        box.addView(label("▤",32,MINT));TextView h=label(heading,18,WHITE);LayoutParams p=new LayoutParams(-1,-2);p.topMargin=dp(14);box.addView(h,p);
        TextView d=label(detail,12,MUTED);d.setLineSpacing(dp(7),1);LayoutParams q=new LayoutParams(-1,-2);q.topMargin=dp(12);box.addView(d,q);body.addView(box,new LayoutParams(-1,-2));
    }
    private void card(JSONObject item){
        LinearLayout card=new LinearLayout(activity);card.setOrientation(VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(surface(false));card.setFocusable(true);
        LinearLayout top=row();TextView name=label(item.optString("name"),16,WHITE);name.setTypeface(null,Typeface.BOLD);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);top.addView(name,new LayoutParams(0,-2,1));
        TextView count=label("×"+item.optInt("quantity")+" "+item.optString("unit"),13,MINT);top.addView(count);card.addView(top);
        JSONObject life=item.optJSONObject("shelfLife");String date="未提供保质期",status=item.optString("reminder","");int color=AMBER;
        if(life!=null){date=life.optString("startDate").replace('-','.')+" — "+life.optString("endDate").replace('-','.');status=expiry(life.optString("endDate"));color=status.contains("已过期")?0xffed9c95:days(life.optString("endDate"))<=7?AMBER:MINT;}
        LinearLayout bottom=row();bottom.addView(label(date,9,MUTED),new LayoutParams(0,-2,1));bottom.addView(label(status,9,color));
        LayoutParams bp=new LayoutParams(-1,-2);bp.topMargin=dp(6);card.addView(bottom,bp);
        card.setContentDescription(item.optString("name")+"，"+count.getText()+"，"+date+"，"+status);
        LayoutParams cp=new LayoutParams(-1,-2);cp.bottomMargin=dp(7);body.addView(card,cp);
    }
    private long days(String end){try{SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setLenient(false);Calendar today=Calendar.getInstance();today.set(Calendar.HOUR_OF_DAY,0);today.set(Calendar.MINUTE,0);today.set(Calendar.SECOND,0);today.set(Calendar.MILLISECOND,0);return (format.parse(end).getTime()-today.getTimeInMillis())/86400000L;}catch(Exception e){return Long.MAX_VALUE;}}
    private String expiry(String end){long d=days(end);return d==Long.MAX_VALUE?"日期待确认":d<0?"已过期 "+(-d)+" 天":d==0?"今天到期":d+" 天后到期";}
    public void focusTabs(){final int target=fridges.length()==0?0:selected;post(()->{selected=target;tabs.getChildAt(target).requestFocus();ready=true;refresh();scroll.smoothScrollTo(Math.max(0,tabs.getChildAt(target).getLeft()-dp(10)),0);});}
    private void manage(){ArrayList<String> options=new ArrayList<>();options.add("添加冰箱");if(fridges.length()>0){options.add("重命名当前冰箱");options.add("删除当前冰箱");}options.add("收起侧栏");new AlertDialog.Builder(activity).setTitle("管理冰箱").setItems(options.toArray(new String[0]),(d,w)->{if(w==0)edit(false);else if(w==options.size()-1)close.run();else if(w==1)edit(true);else delete();}).setNegativeButton("取消",null).show();}
    private void edit(boolean renaming){
        if(!renaming&&fridges.length()>=20){Toast.makeText(activity,"最多添加 20 台冰箱",Toast.LENGTH_SHORT).show();return;}
        final int index=selected;EditText input=new EditText(activity);input.setSingleLine(true);input.setText(renaming?name(index):"冰箱"+(fridges.length()+1));input.selectAll();input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});
        LinearLayout box=row();box.setPadding(dp(24),dp(8),dp(24),dp(8));box.addView(input,new LayoutParams(-1,-2));
        AlertDialog d=new AlertDialog.Builder(activity).setTitle(renaming?"重命名冰箱":"添加冰箱 Tab").setView(box).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
            String text=input.getText().toString().trim();if(text.isEmpty()){input.setError("请输入冰箱名称");return;}for(int i=0;i<fridges.length();i++)if((!renaming||i!=index)&&text.equals(name(i))){input.setError("该名称已存在");return;}
            try{if(renaming)fridge(index).put("name",text);else{JSONObject f=new JSONObject();f.put("id",UUID.randomUUID().toString());f.put("name",text);f.put("inventoryStatus","待录入");f.put("items",new JSONArray());fridges.put(f);selected=fridges.length()-1;}}catch(JSONException ignored){}
            save();d.dismiss();rebuild(true);
        }));d.show();
    }
    private void delete(){final int index=selected;new AlertDialog.Builder(activity).setTitle("删除「"+name(index)+"」？").setMessage("这将同时删除该冰箱的本机食材清单。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{fridges.remove(index);selected=Math.max(0,Math.min(index,fridges.length()-1));save();rebuild(true);}).show();}
}
