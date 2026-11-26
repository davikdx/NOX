package br.app.nox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    public Store store;
    public SleepPages sleep;
    public HistoryPages journal;
    public AlarmPages alarms;
    private DataActions dataActions;
    public LinearLayout body;
    public int ink, muted, surface, accent, bg;
    private int border, currentTab;
    private String pageTitle = "";
    private boolean dark, ringOpened, foreground, exactOnPause;
    private TextView countdown;
    private long countdownAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        public void run() {
            if (!foreground) return;
            if (countdown != null) {
                long mins = (countdownAt - System.currentTimeMillis() + 59999) / 60000;
                if (mins <= 0) { home(); }
                else countdown.setText("Daqui a " + duration(mins));
            }
            handler.postDelayed(this, 20000);
        }
    };
    static final Locale BR = new Locale("pt","BR");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm",BR);

    @Override public void onCreate(Bundle state) {
        store = new Store(this);
        String theme = config().optString("theme","system");
        dark = theme.equals("dark") || (theme.equals("system") &&
                (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark ? R.style.NoxDark : R.style.NoxLight);
        super.onCreate(state);
        RequestPalette();
        alarms = new AlarmPages(this);
        sleep = new SleepPages(this);
        journal = new HistoryPages(this);
        dataActions = new DataActions(this);
        try { Scheduler.sync(this); } catch (RuntimeException ignored) { /* Permission state is visible in diagnostics. */ }
        try { ReminderReceiver.sync(this); } catch (RuntimeException ignored) {}
        if (alarms.restoreState(state)) return;
        if (state != null && state.containsKey("tab") && !getIntent().hasExtra("night") && !getIntent().hasExtra("screen")) {
            if ("Configurações".equals(state.getString("title"))) settings();
            else openTab(state.getInt("tab"));
        } else route(getIntent());
    }

    private void RequestPalette() {
        ink = dark ? 0xfff5f3ff : 0xff201d36;
        muted = dark ? 0xffbfbbce : 0xff5e5a73;
        surface = dark ? 0xff212234 : 0xffffffff;
        accent = dark ? 0xffc0b2ff : 0xff53428f;
        bg = dark ? 0xff10111c : 0xfff6f5fc;
        border = dark ? 0xff34354a : 0xffdfdce9;
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
    }

    private void route(Intent intent) {
        String night = intent.getStringExtra("night");
        if (night != null && store.get("night",night) != null) { sleep.morning(night); return; }
        String screen = intent.getStringExtra("screen");
        if ("sleep".equals(screen)) sleep.show();
        else if ("day".equals(screen)) sleep.day();
        else if ("alarm".equals(screen) || "alarms".equals(screen)) alarms.show();
        else if ("history".equals(screen)) history();
        else if (!config().optBoolean("onboarded")) welcome();
        else home();
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); route(intent); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); if (pageTitle.equals("Configurar alarme") || pageTitle.equals("Som e soneca")) alarms.saveState(state); state.putInt("tab",currentTab); state.putString("title",pageTitle);
    }
    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        if (alarms == null) return;
        if (Scheduler.ringing(this) != null) {
            if (!ringOpened) {
                ringOpened = true;
                startActivity(new Intent(this,RingActivity.class));
            }
        } else ringOpened = false;
        if (!exactOnPause && Scheduler.exact(this)) {
            try { Scheduler.sync(this); } catch (RuntimeException ignored) {}
        }
        if (pageTitle.equals("Confiabilidade do despertador")) diagnostics();
        handler.removeCallbacks(tick); handler.post(tick);
    }

    @Override protected void onPause() {
        super.onPause(); alarms.stopPreview();
        foreground = false;
        handler.removeCallbacks(tick);
        exactOnPause = Scheduler.exact(this);
    }
    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request,result,intent);
        if (request >= 400 && request < 430) alarms.result(request,result,intent);
        else if (request >= 600 && request < 620) journal.result(request,result,intent);
        else dataActions.result(request,result,intent);
    }

    public JSONObject config() {
        return J.merged(J.obj("id","main","goal",480,"latency",20,"bedMinute",22*60+45,"wakeMinute",7*60,
                "window",15,"snooze",5,"maxSnoozes",0,"volume",80,"tone","nox","vibrate",true,"ramp",true,
                "personal",true,"theme","system"),store.get("setting","main"));
    }

    public void saveConfig(JSONObject cfg) { J.put(cfg,"id","main"); store.put("setting",cfg); }
    public int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    static String minute(int value) { return CLOCK.format(LocalTime.of(Math.floorMod(value,1440)/60,Math.floorMod(value,60))); }
    static String clock(long at) { return CLOCK.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())); }
    static String duration(long mins) {
        if (mins < 60) return mins + " min";
        return mins/60 + " h" + (mins%60 == 0 ? "" : " " + mins%60 + " min");
    }
    public void message(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }

    private GradientDrawable shape(int color, int radius, boolean stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (stroke) d.setStroke(dp(1),border);
        return d;
    }
    private TextView text(String value, float size, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif",Typeface.NORMAL));
        t.setIncludeFontPadding(false); return t;
    }
    private void append(View v, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(top); body.addView(v,p);
    }
    public TextView label(String value) { TextView t=text(value,16,ink,false); append(t,8); return t; }
    public void note(String value) { TextView t=text(value,14,muted,false); t.setLineSpacing(dp(2),1); append(t,7); }
    public void heading(String value) { TextView t=text(value,20,ink,true); t.setAccessibilityHeading(true); append(t,22); }
    public Button button(String value,Runnable action) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(16);
        b.setTextColor(dark?0xff201c39:Color.WHITE); b.setMinHeight(dp(52));
        b.setPadding(dp(16),dp(10),dp(16),dp(10)); b.setElevation(0); b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x335f5680),shape(accent,24,false),null));
        b.setOnClickListener(v -> action.run()); append(b,12); return b;
    }
    public void card(String title,String subtitle,Runnable action) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(14),dp(18),dp(14)); box.setMinimumHeight(dp(56));
        box.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226f599b),shape(surface,18,true),null));
        box.addView(text(title,17,ink,true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub=text(subtitle,14,muted,false); sub.setPadding(0,dp(5),0,0); box.addView(sub);
        }
        if(action!=null) { box.setOnClickListener(v -> action.run()); box.setFocusable(true); }
        append(box,8);
    }

    public void page(String title,String subtitle,int tab) {
        alarms.stopPreview(); currentTab = tab; pageTitle = title; countdown = null;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets;
        });
        LinearLayout tool = new LinearLayout(this); tool.setGravity(Gravity.CENTER_VERTICAL);
        tool.setPadding(dp(18),0,dp(8),0);
        ImageView mark = new ImageView(this); mark.setImageResource(R.mipmap.ic_launcher); mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tool.addView(mark,new LinearLayout.LayoutParams(dp(28),dp(28)));
        TextView brand=text("Nox",21,ink,true); brand.setPadding(dp(8),0,0,0);
        tool.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Icon settingsButton=new Icon(4,"Configurações",muted);
        settingsButton.setOnClickListener(v->settings());
        tool.addView(settingsButton,new LinearLayout.LayoutParams(dp(48),dp(48)));
        root.addView(tool,new LinearLayout.LayoutParams(-1,dp(54)));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(7),dp(18),dp(26)); scroll.addView(body);
        TextView h=text(title,32,ink,true); h.setAccessibilityHeading(true); append(h,4);
        if(subtitle != null && !subtitle.isEmpty()) note(subtitle);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav = new LinearLayout(this); nav.setBackgroundColor(surface); nav.setPadding(dp(4),dp(4),dp(4),dp(4));
        String[] labels={"Início","Alarmes","Sono","Histórico"};
        for(int i=0;i<labels.length;i++) {
            final int dest=i; int color=dest==tab?accent:muted;
            LinearLayout item=new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
            if(dest==tab) item.setBackground(shape(dark?0xff303047:0xffeeebfa,18,false));
            item.setPadding(0,dp(4),0,dp(4));
            Icon icon=new Icon(i,null,color); item.addView(icon,new LinearLayout.LayoutParams(dp(26),dp(24)));
            TextView name=text(labels[i],12,color,dest==tab); item.addView(name);
            item.setContentDescription(labels[i]); item.setSelected(dest==tab); item.setFocusable(true);
            item.setMinimumHeight(dp(50)); item.setOnClickListener(v->openTab(dest));
            nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(nav); setContentView(root); root.requestApplyInsets();
    }

    private final class Icon extends View {
        final int type; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(int type,String name,int color) {
            super(MainActivity.this); this.type=type; p.setColor(color); p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.65f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            if(name!=null) {setContentDescription(name);setFocusable(true);}
            else setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas c) {
            int size=Math.min(Math.min(getWidth(),getHeight()),dp(23));
            c.save(); c.translate((getWidth()-size)/2f,(getHeight()-size)/2f); c.scale(size/24f,size/24f);
            Path v=new Path();
            if(type==0) {
                v.moveTo(3,10);v.lineTo(12,3);v.lineTo(21,10);v.lineTo(21,21);v.lineTo(15,21);v.lineTo(15,14);v.lineTo(9,14);v.lineTo(9,21);v.lineTo(3,21);v.close();c.drawPath(v,p);
            } else if(type==1) {
                c.drawCircle(12,13,7.6f,p);c.drawLine(12,8,12,13,p);c.drawLine(12,13,15,15,p);
                c.drawLine(3,6,6,3,p);c.drawLine(18,3,21,6,p);
            } else if(type==2) {
                v.moveTo(16,3);v.cubicTo(6,3,3,15,11,20);v.cubicTo(15,23,20,21,22,17);v.cubicTo(14,18,11,9,16,3);v.close();c.drawPath(v,p);
            } else if(type==3) {
                c.drawLine(5,19,5,12,p);c.drawLine(12,19,12,5,p);c.drawLine(19,19,19,9,p);
            } else {
                for(int i=5;i<=19;i+=7)c.drawCircle(12,i,1.2f,p);
            }
            c.restore();
        }
    }

    private void openTab(int tab) {
        switch(tab) { case 1: alarms.show(); break; case 2: sleep.show(); break; case 3: history(); break; default: home(); }
    }
    public void history() { journal.show(); }
    public void diagnostics() { alarms.diagnostics(); }

    private void welcome() {
        page("Seu sono, seu ritmo.","Escolha sua rotina e faça um disparo de teste antes da primeira noite.",0);
        landscape("Bem-vindo ao Nox","Uma noite de cada vez",false);
        button("Configurar o despertador",()->{ finishWelcome(); diagnostics(); });
        card("Escolher minha rotina","Horários, meta de sono e janela de despertar.",()->{ finishWelcome(); sleep.plan(); });
        card("Ir para o início","Você pode configurar tudo depois.",()->{finishWelcome();home();});
    }
    private void finishWelcome() { JSONObject cfg=config(); J.put(cfg,"onboarded",true); saveConfig(cfg); }

    private void landscape(String top,String value,boolean dawn) {
        FrameLayout hero=new FrameLayout(this);
        hero.setBackground(shape(0xff252445,22,false)); hero.setClipToOutline(true);
        hero.addView(new Nightscape(this,dawn),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout layer=new LinearLayout(this); layer.setOrientation(LinearLayout.VERTICAL);
        layer.setGravity(Gravity.CENTER_VERTICAL); layer.setPadding(dp(20),dp(19),dp(12),dp(18));
        layer.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xbb16152f,0x00151535}));
        layer.addView(text(top,15,0xfff3eeff,true)); TextView valueView=text(value,34,Color.WHITE,true);
        valueView.setPadding(0,dp(6),0,dp(2)); layer.addView(valueView);
        if (countdownAt > 0 && top.equals("Próximo despertar")) {
            countdown=text("Daqui a "+duration(Math.max(0,(countdownAt-System.currentTimeMillis()+59999)/60000)),14,0xfff1edff,false);
            layer.addView(countdown);
        }
        hero.addView(layer,new FrameLayout.LayoutParams(-1,-2)); append(hero,16);
    }

    public void home() {
        JSONObject cfg=config(); int hour=LocalTime.now().getHour();
        page(hour<5||hour>=18?"Boa noite.":hour<12?"Bom dia.":"Boa tarde.","",0);
        JSONObject next=Scheduler.next(this);
        countdownAt=next==null?0:next.optLong("at");
        if(next==null) landscape("Próximo despertar","Sem alarme ativo",hour>=5&&hour<12);
        else {
            landscape("Próximo despertar",clock(countdownAt),hour>=5&&hour<12);
            String when=Instant.ofEpochMilli(countdownAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEEE, dd/MM",BR));
            note(next.optString("name","Alarme")+" · "+when);
        }
        List<JSONObject> nights=store.all("night");
        nights.sort((a,b)->Long.compare(b.optLong("bed"),a.optLong("bed")));
        long now=System.currentTimeMillis();
        JSONObject pending=null, last=null; long sum=0; int n=0;
        for(JSONObject night:nights) {
            long bed=night.optLong("bed"), wake=night.optLong("wake");
            if (bed>0 && bed<=now && !night.optBoolean("closed") && night.optLong("up")==0 && now-bed<=36*3600000L && pending==null) pending=night;
            if(wake<=0) continue;
            if(last==null) last=night;
            int minutes=estimated(night);
            if(wake>=now-7*86400000L&&minutes>=0) { sum+=minutes; n++; }
        }
        if(pending!=null) button(pending.optLong("wake")>0?"Registrar que levantei":"Abrir sessão de sono",()->sleep.show());
        else button("Vou dormir agora",()->sleep.startNow());
        String bed=minute(cfg.optInt("wakeMinute",420)-cfg.optInt("goal",480)-cfg.optInt("latency",20));
        card("Deitar às "+bed,"Para uma meta de "+duration(cfg.optInt("goal",480))+" e despertar às "+minute(cfg.optInt("wakeMinute",420))+".",()->sleep.plan());
        card("Criar alarme","Escolher horário, dias e som.",()->alarms.edit(null));
        if(last!=null) {
            JSONObject selected=last;
            String quality=quality(last.optInt("quality",0));
            heading("Última noite");
            int mins=estimated(last);
            card(mins<0?"Duração pendente":duration(mins)+" de sono estimado",
                    quality+" · Despertou às "+clock(last.optLong("wake")),()->sleep.morning(selected.optString("id")));
            if(n>0) note("Nos últimos 7 dias, a média estimada foi de "+duration(Math.round((double)sum/n))+" em "+n+(n==1?" registro.":" registros."));
        }
        if(!Scheduler.exact(this)) card("Autorizar alarmes exatos","O Android precisa dessa permissão para agendar o despertar.",this::diagnostics);
    }
    static int estimated(JSONObject night) {
        long bed=night.optLong("bed"),wake=night.optLong("wake"); int latency=night.optInt("reportedLatency",night.optInt("latencyBandEstimate",night.optInt("latency",-1)));
        if(bed<=0||wake<=bed||latency<0) return -1;
        long minutes=(wake-bed)/60000-latency-night.optInt("waso",0);
        return minutes<0||minutes>1440 ? -1 : (int)minutes;
    }
    static String quality(int value) {
        String[] options={"Qualidade pendente","Péssima","Ruim","Regular","Boa","Excelente"};
        return value<1||value>5?options[0]:options[value];
    }

    public void settings() {
        page("Configurações","", -1);
        card("Confiabilidade do despertador","Permissões, volume e teste com a tela bloqueada.",this::diagnostics);
        card("Horários e meta de sono","Rotina, latência e janela de despertar.",()->sleep.plan());
        card("Som e soneca","Padrões de música, volume, vibração e repetição.",()->alarms.defaults());
        card("Personalização","Consulte e ajuste os parâmetros usados nas sugestões.",()->sleep.learned());
        card("Lembretes","Rotina noturna e registro de sonolência.",()->sleep.reminders());
        String[] labels={"Acompanhar o sistema","Claro","Escuro"};
        String[] values={"system","light","dark"};
        int index=Arrays.asList(values).indexOf(config().optString("theme","system"));
        card("Aparência",labels[Math.max(index,0)],()->new AlertDialog.Builder(this).setTitle("Aparência")
                .setSingleChoiceItems(labels,index,(d,i)->{JSONObject c=config();J.put(c,"theme",values[i]);saveConfig(c);d.dismiss();recreate();}).show());
        heading("Seus dados");
        card("Salvar backup local","Arquivo JSON com histórico, configurações e alarmes.",()->dataActions.backup());
        card("Restaurar backup","Escolher um arquivo e revisar antes de substituir.",()->dataActions.restore());
        card("Excluir histórico","Apagar noites e avaliações diurnas deste aparelho.",()->new AlertDialog.Builder(this)
                .setTitle("Excluir todo o histórico?").setMessage("As noites, avaliações e registros diurnos serão apagados. Os horários de alarme continuarão programados.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Excluir histórico",(d,w)->{store.clearHistory();message("Histórico excluído.");settings();}).show());
        note("Nox 1.0 · Dados no aparelho. A exportação e o compartilhamento dependem de uma ação sua.");
    }

    @Override public void onBackPressed() {
        if (currentTab==0 && (pageTitle.startsWith("Boa ")||pageTitle.startsWith("Bom ")||pageTitle.equals("Seu sono, seu ritmo."))) super.onBackPressed();
        else if (currentTab==3 && !pageTitle.equals("Histórico")) history();
        else if (currentTab==1 && !pageTitle.equals("Alarmes")) alarms.show();
        else if (currentTab==2 && !pageTitle.equals("Sono")) sleep.show();
        else home();
    }
}

