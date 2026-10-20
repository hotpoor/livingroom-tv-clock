package cn.livingroom.flipclock;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

/** Foreground IME compatibility test; does not record audio or save utterances. */
public class VoiceTestActivity extends Activity {
    private EditText input;
    private TextView status;
    private boolean shownKeyboard;
    private int changes;
    public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(48),dp(32),dp(48),dp(24));root.setBackgroundColor(0xff161a24);
        TextView title=text("遥控器语音输入测试",26,0xffedf0f6);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        TextView hint=text("输入框已选中。按遥控器语音键，说一句：\n「明天下午四点提醒我买牛奶」",18,0xffbbc4d4);hint.setLineSpacing(dp(7),1);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(16);hp.bottomMargin=dp(18);root.addView(hint,hp);
        input=new EditText(this);input.setHint("观察识别文字是否出现在这里");input.setTextColor(0xfff2f4fa);input.setHintTextColor(0xff8995a8);input.setTextSize(22);input.setGravity(Gravity.TOP);input.setPadding(dp(16),dp(12),dp(16),dp(12));input.setBackgroundColor(0xff272f40);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        root.addView(input,new LinearLayout.LayoutParams(-1,dp(108)));
        status=text("等待输入 · 测试内容不会保存为日程",14,0xffa7d9c8);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(12);sp.bottomMargin=dp(10);root.addView(status,sp);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){changes++;status.setText(s.length()==0?"输入框为空":"已收到 "+s.length()+" 个字符 · 请核对文字");android.util.Log.i("FlipClockVoice","Input changed; length="+s.length()+"; changes="+changes);}public void afterTextChanged(Editable e){}});
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button keyboard=button("打开输入法");keyboard.setOnClickListener(v->showKeyboard());actions.addView(keyboard,new LinearLayout.LayoutParams(0,dp(48),1));
        Button clear=button("清空");clear.setOnClickListener(v->{input.setText("");input.requestFocus();});actions.addView(clear,new LinearLayout.LayoutParams(0,dp(48),1));
        Button finish=button("结束测试");finish.setOnClickListener(v->finish());actions.addView(finish,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(actions);
        setContentView(root);input.requestFocus();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private void showKeyboard(){input.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);}
    public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&!shownKeyboard){shownKeyboard=true;input.post(()->showKeyboard());}}
    protected void onResume(){super.onResume();android.util.Log.i("FlipClockVoice","Voice input screen resumed");}
    protected void onPause(){android.util.Log.i("FlipClockVoice","Voice input screen paused");super.onPause();}
    public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN&&(e.getKeyCode()==KeyEvent.KEYCODE_SEARCH||e.getKeyCode()==KeyEvent.KEYCODE_VOICE_ASSIST||e.getKeyCode()==KeyEvent.KEYCODE_ASSIST))android.util.Log.i("FlipClockVoice","Assistant key received: "+e.getKeyCode());return super.dispatchKeyEvent(e);}
}
