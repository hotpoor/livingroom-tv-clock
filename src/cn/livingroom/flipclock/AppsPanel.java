package cn.livingroom.flipclock;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** TV-focusable grid of installed launchable applications. */
final class AppsPanel extends LinearLayout {
    private final GridView grid;
    private final ArrayList<ResolveInfo> apps=new ArrayList<>();
    private final HashMap<String,Intent> launches=new HashMap<>();
    private final Activity activity;
    AppsPanel(Activity a){
        super(a);activity=a;setOrientation(VERTICAL);setPadding(dp(24),dp(12),dp(24),dp(12));
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff171b23,0xff101218}));
        LinearLayout heading=new LinearLayout(a);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(a);title.setText("应用");title.setTextColor(0xffeeeeef);title.setTextSize(20);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new LayoutParams(0,-2,1));
        TextView hint=new TextView(a);hint.setText("方向键选择 · 确认打开 · 返回时钟");hint.setTextSize(12);hint.setTextColor(0xff969eae);heading.addView(hint);addView(heading);
        PackageManager pm=a.getPackageManager();
        // Prefer the TV entry when an app also publishes a phone launcher entry.
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            Intent query=new Intent(Intent.ACTION_MAIN).addCategory(category);
            for(ResolveInfo info:pm.queryIntentActivities(query,0)){
                String pkg=info.activityInfo.packageName;
                if(pkg.equals(a.getPackageName())||launches.containsKey(pkg))continue;
                launches.put(pkg,new Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(new ComponentName(pkg,info.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));apps.add(info);
            }
        }
        Collections.sort(apps,(x,y)->{int px=priority(x.activityInfo.packageName),py=priority(y.activityInfo.packageName);return px!=py?Integer.compare(px,py):java.text.Collator.getInstance(Locale.CHINA).compare(x.loadLabel(pm).toString(),y.loadLabel(pm).toString());});
        apps.add(0,null); // Dedicated website shortcut, ahead of installed apps.
        grid=new GridView(a);grid.setNumColumns(6);grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(10));grid.setPadding(dp(3),dp(8),dp(3),dp(3));grid.setClipToPadding(false);grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        StateListDrawable selector=new StateListDrawable();selector.addState(new int[]{},tile(0xff354154,0xffb9d4ec));grid.setSelector(selector);grid.setDrawSelectorOnTop(false);
        grid.setAdapter(new BaseAdapter(){public int getCount(){return apps.size();}public Object getItem(int p){return apps.get(p);}public long getItemId(int p){return p;}
            public View getView(int position,View recycled,ViewGroup parent){ResolveInfo info=apps.get(position);LinearLayout card=new LinearLayout(a);card.setOrientation(VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(8),dp(6),dp(8),dp(6));card.setLayoutParams(new AbsListView.LayoutParams(-1,dp(72)));card.setBackground(tile(0x442b3442,0xff353e4b));
                ImageView icon=new ImageView(a);if(info==null)icon.setImageResource(android.R.drawable.ic_menu_view);else icon.setImageDrawable(info.loadIcon(pm));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);card.addView(icon,new LayoutParams(dp(32),dp(32)));
                TextView label=new TextView(a);label.setText(info==null?"一帆 TV":info.loadLabel(pm));label.setTextColor(0xfff0f2f5);label.setTextSize(12);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setGravity(Gravity.CENTER);LayoutParams lp=new LayoutParams(-1,-2);lp.topMargin=dp(4);card.addView(label,lp);card.setContentDescription(info==null?"一帆 TV 网站":info.loadLabel(pm));return card;}});
        grid.setOnItemClickListener((parent,view,position,id)->{try{if(apps.get(position)==null)openWebsite();else activity.startActivity(launches.get(apps.get(position).activityInfo.packageName));}catch(ActivityNotFoundException|SecurityException error){Toast.makeText(a,"系统未允许打开该应用",Toast.LENGTH_LONG).show();}});
        addView(grid,new LayoutParams(-1,0,1));
        if(apps.isEmpty()){TextView empty=new TextView(a);empty.setText("暂无可启动的应用");empty.setTextColor(0xffaaaaaa);addView(empty);}
    }
    private void openWebsite(){
        Intent open=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.yifan.tv/"));
        for(String browser:new String[]{"com.tcl.browser","com.phlox.tvwebbrowser"}){
            open.setPackage(browser);
            if(open.resolveActivity(activity.getPackageManager())!=null){activity.startActivity(open);return;}
        }
        Toast.makeText(activity,"请先安装 BrowseHere 或 TV Bro 浏览器",Toast.LENGTH_LONG).show();
    }
    private int priority(String pkg){return pkg.equals("com.google.android.youtube.tv")?0:pkg.equals("com.github.metacubex.clash.meta")?1:2;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable tile(int color,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));d.setStroke(dp(2),border);return d;}
    void focusFirst(){post(()->{grid.requestFocus();grid.setSelection(0);});}
}
