package br.app.nox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

public final class RingActivity extends Activity {
    private static final int BG=0xff111221,WHITE=0xfff5f4ff,SUB=0xffc7c6d9,ACCENT=0xffbcb1ff;
    private final Handler tick = new Handler(Looper.getMainLooper());
    private JSONObject current;
    private Button snooze;
    private String review = "";
    private final BroadcastReceiver listener = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { render(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (state!=null) review=state.getString("review","");
        render();
        if (!isFinishing()) handle(getIntent());
    }
    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(RingService.STATE);
        if (Build.VERSION.SDK_INT>=33) registerReceiver(listener,f,Context.RECEIVER_NOT_EXPORTED);
        else registerLegacyListener(f);
        render();
    }
    // API 29–32 has no NOT_EXPORTED flag. Require this app's signature permission instead.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacyListener(IntentFilter filter) {
        registerReceiver(listener,filter,getPackageName()+".permission.RING_STATE",null);
    }
    @Override protected void onStop() {
        tick.removeCallbacksAndMessages(null);
        unregisterReceiver(listener);
        super.onStop();
    }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); render(); handle(i); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("review",review); }
    private void handle(Intent i) {
        if (i==null || !RingService.UP.equals(i.getAction()) || current==null) return;
        i.setAction("OPEN");
        if (current.optString("id").equals(i.getStringExtra("id")) && current.optLong("_token")==i.getLongExtra("token",0))
            action(RingService.UP);
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView v=new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        return v;
    }
    private Button button(LinearLayout parent,String label,int fill,int color,Runnable action) {
        Button b=new Button(this);
        b.setAllCaps(false); b.setText(label); b.setTextColor(color); b.setTextSize(17);
        b.setLetterSpacing(0); b.setMinHeight(dp(56)); b.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable shape=new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(26));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x32ffffff),shape,null));
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(10);
        parent.addView(b,lp); return b;
    }
    private static String duration(long minutes) {
        return minutes/60+" h "+String.format(java.util.Locale.ROOT,"%02d",minutes%60)+" min";
    }

    private void render() {
        if (isFinishing()) return;
        tick.removeCallbacksAndMessages(null);
        current=Scheduler.ringing(this);
        if (current==null) {
            if (!review.isEmpty()) {
                startActivity(new Intent(this,MainActivity.class).putExtra("night",review)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                review="";
            }
            finishAndRemoveTask(); return;
        }
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BG); scroll.setFitsSystemWindows(true);
        ViewGroup root = (ViewGroup)findViewById(android.R.id.content);
        root.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 35) scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return WindowInsets.CONSUMED;
        });
        LinearLayout shell=new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(shell,new ScrollView.LayoutParams(-1,-2));
        FrameLayout hero=new FrameLayout(this); shell.addView(hero,new LinearLayout.LayoutParams(-1,dp(164)));
        hero.addView(new Nightscape(this,true),new FrameLayout.LayoutParams(-1,-1));
        TextView brand=text("Nox",22,WHITE,true);
        FrameLayout.LayoutParams brandLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);
        brandLp.setMargins(dp(26),dp(22),0,0); hero.addView(brand,brandLp);
        LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24),dp(18),dp(24),dp(24)); shell.addView(panel,new LinearLayout.LayoutParams(-1,-2));
        panel.addView(text(current.optString("name","Despertador"),23,WHITE,true));
        TextClock clock=new TextClock(this); clock.setFormat12Hour("HH:mm"); clock.setFormat24Hour("HH:mm");
        clock.setTextSize(64); clock.setTextColor(WHITE); clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        panel.addView(clock);
        String details="Primeiro horário programado  ·  "+RingService.time(current.optLong("_base",current.optLong("_token")));
        if (current.optInt("_count")>0) details+="\nDisparo desta soneca  ·  "+RingService.time(current.optLong("_token"));
        long limit=current.optLong("deadline");
        if (limit>0) details+="\nHorário-limite  ·  "+RingService.time(limit);
        JSONObject n=new Store(this).get("night",current.optString("night",""));
        if (n!=null && n.optLong("bed")>0 && n.has("latency")) {
            long len=(System.currentTimeMillis()-n.optLong("bed"))/60000-n.optInt("latency");
            if (len>=0) details+="\nSono estimado  ·  "+duration(len);
        }
        TextView facts=text(details,16,SUB,false); facts.setLineSpacing(dp(2),1); facts.setPadding(0,0,0,dp(12));
        panel.addView(facts);
        button(panel,"Despertei",ACCENT,0xff18132f,()->action(RingService.WAKE));
        button(panel,"Levantei",0xff37364e,WHITE,()->action(RingService.UP));
        snooze=button(panel,"",0xff232538,WHITE,this::requestSnooze);
        setContentView(scroll);
        clockTick();
    }
    private void clockTick() {
        if (current==null || snooze==null) return;
        String label = RingService.snoozeLabel(current,System.currentTimeMillis());
        snooze.setText(label.startsWith("+") ? label+"  ·  Ainda não despertei" : label);
        tick.postDelayed(this::clockTick,1000);
    }
    private void requestSnooze() {
        if (current==null) return;
        long now=System.currentTimeMillis(), target=Scheduler.snoozeTarget(current,now);
        if (target==0 || !Scheduler.exact(this)) {
            String msg = !Scheduler.exact(this) ? "A permissão para programar a soneca está indisponível."
                : (current.optLong("deadline")>0 && now>=current.optLong("deadline")
                    ? "Você chegou ao seu horário-limite." : "Você atingiu o limite de sonecas.");
            new AlertDialog.Builder(this).setTitle("O alarme continua tocando").setMessage(msg)
                .setPositiveButton("Continuar tocando",null)
                .setNegativeButton("Despertei",(d,w)->action(RingService.WAKE)).show();
            return;
        }
        if (current.optLong("deadline")==target && target<now+current.optInt("snooze",5)*60000L) {
            new AlertDialog.Builder(this).setTitle("Soneca até "+RingService.time(target))
                .setMessage("O próximo disparo será no seu horário-limite.")
                .setPositiveButton("Adiar até o limite",(d,w)->action(RingService.SNOOZE))
                .setNegativeButton("Continuar tocando",null).show();
        } else action(RingService.SNOOZE);
    }
    private void action(String action) {
        JSONObject a=current;
        if (a==null) return;
        String night=RingService.act(this,a.optString("id"),a.optLong("_token"),action);
        if (RingService.UP.equals(action) && night!=null && !night.isEmpty()) review=night;
        render();
    }
}
