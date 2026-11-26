package br.app.nox;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.widget.Toast;
import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** One foreground audio owner for every queued occurrence. Alarm dismissal is token-bound. */
public final class RingService extends Service {
    static final String CHANNEL = "nox.ring.v1";
    static final String STATE = "br.app.nox.RING_STATE";
    static final String SNOOZE = "br.app.nox.SNOOZE", WAKE = "br.app.nox.WAKE", UP = "br.app.nox.UP";
    static final int NOTICE = 17401;
    private static RingService live;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioFocusRequest focus;
    private ToneGenerator emergencyTone;
    private Vibrator vibrator;
    private PowerManager.WakeLock awake;
    private String current = "";
    private long token;
    private int sequence;
    private boolean foreground;

    static String time(long t) {
        return DateTimeFormatter.ofPattern("HH:mm",Locale.ROOT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(t));
    }

    static void channel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL,"Despertador",NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Controles do alarme e exibição na tela bloqueada");
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setSound(null,null);
        ch.enableVibration(false);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    static PendingIntent ui(Context c,JSONObject a,String action) {
        Intent i = new Intent(c,RingActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .setData(key(a,action)).setAction(action).putExtra("id",a.optString("id")).putExtra("token",a.optLong("_token"));
        return PendingIntent.getActivity(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static Uri key(JSONObject a,String action) {
        return new Uri.Builder().scheme("nox").authority("ring").appendPath(a.optString("id"))
            .appendPath(String.valueOf(a.optLong("_token"))).appendPath(action).build();
    }
    private static PendingIntent command(Context c,JSONObject a,String action) {
        Intent i = new Intent(c,RingService.class).setData(key(a,action)).setAction(action)
            .putExtra("id",a.optString("id")).putExtra("token",a.optLong("_token"));
        return PendingIntent.getForegroundService(c,0,i,PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    static String snoozeLabel(JSONObject a,long now) {
        long target = Scheduler.snoozeTarget(a,now);
        if (target == 0) return "Soneca indisponível";
        if (a.optLong("deadline") == target && target < now + a.optInt("snooze",5)*AlarmTimes.MINUTE)
            return "Adiar até "+time(target);
        return "+"+a.optInt("snooze",5)+" min";
    }

    private static Notification note(Context c,JSONObject a,boolean fullScreen) {
        channel(c);
        Notification.Builder b = new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_alarm)
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setContentTitle(a.optString("name","Despertador"))
            .setContentText("Programado para "+time(a.optLong("_base",a.optLong("_token"))))
            .setContentIntent(ui(c,a,"OPEN"));
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (fullScreen && (Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()))
            b.setFullScreenIntent(ui(c,a,"OPEN"),true);
        long now = System.currentTimeMillis();
        if (Scheduler.snoozeTarget(a,now) > 0 && Scheduler.exact(c)) {
            b.addAction(new Notification.Action.Builder(null,snoozeLabel(a,now),command(c,a,SNOOZE)).build());
        }
        b.addAction(new Notification.Action.Builder(null,"Despertei",command(c,a,WAKE)).build());
        b.addAction(new Notification.Action.Builder(null,"Levantei",ui(c,a,UP)).build());
        return b.build();
    }

    static void notifyFallback(Context c) {
        try {
            JSONObject a = Scheduler.ringing(c);
            if (a!=null) c.getSystemService(NotificationManager.class).notify(NOTICE,note(c,a,true));
        } catch (RuntimeException e) { Log.w("Nox","Notificação indisponível",e); }
    }

    @Override public void onCreate() {
        super.onCreate();
        live = this;
        channel(this);
        awake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Nox:despertador");
        awake.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent i,int flags,int startId) {
        JSONObject a = Scheduler.ringing(this);
        if (a == null) {
            stopSelf(startId);
            AlarmReceiver.release();
            return START_NOT_STICKY;
        }
        // Android requires promotion before starting media or requesting audio focus.
        Notification notification = note(this,a,true);
        startForeground(NOTICE,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        foreground = true;
        AlarmReceiver.release();
        if (i != null && (WAKE.equals(i.getAction()) || SNOOZE.equals(i.getAction()))) {
            act(this,i.getStringExtra("id"),i.getLongExtra("token",0),i.getAction());
        }
        reconcile();
        return Scheduler.ringing(this)==null ? START_NOT_STICKY : START_STICKY;
    }

    /** null means stale occurrence or rejected snooze. Empty means success without a diary. */
    static String act(Context c,String id,long token,String action) {
        if (id==null || token<=0) return null;
        try {
            JSONObject row = new Store(c).get("alarm",id);
            if (row == null || row.optLong("_token") != token) return null;
            String night;
            if (SNOOZE.equals(action)) {
                if (!Scheduler.snooze(c,id,token)) {
                    Toast.makeText(c,"Limite atingido. O despertador continua tocando.",Toast.LENGTH_LONG).show();
                    return null;
                }
                night = "";
            } else if (UP.equals(action) || WAKE.equals(action)) {
                night = Scheduler.dismiss(c,id,token,action);
            } else return null;
            refresh(c);
            return night;
        } catch (RuntimeException e) {
            Log.w("Nox","Falha ao atualizar a ocorrência",e);
            Toast.makeText(c,"Não foi possível concluir a ação. Confira o despertador.",Toast.LENGTH_LONG).show();
            refresh(c);
            return null;
        }
    }

    static void refresh(Context c) {
        if (live != null) live.reconcile();
        else if (Scheduler.ringing(c)==null) c.getSystemService(NotificationManager.class).cancel(NOTICE);
        c.sendBroadcast(new Intent(STATE).setPackage(c.getPackageName()));
    }

    private void reconcile() {
        JSONObject a = Scheduler.ringing(this);
        if (a==null) {
            stopSound();
            if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
            stopSelf();
            return;
        }
        long until = a.optLong("_since") + Scheduler.RING_WINDOW - System.currentTimeMillis();
        if (until <= 0) {
            Scheduler.dismiss(this,a.optString("id"),a.optLong("_token"),"timeout");
            main.post(this::reconcile);
            sendBroadcast(new Intent(STATE).setPackage(getPackageName()));
            return;
        }
        if (current.equals(a.optString("id")) && token == a.optLong("_token")) return;
        stopSound();
        current = a.optString("id");
        token = a.optLong("_token");
        int generation = sequence;
        awake.acquire(Math.min(until,Scheduler.RING_WINDOW)+5000);
        if (foreground) getSystemService(NotificationManager.class).notify(NOTICE,note(this,a,true));
        AudioAttributes attributes = attributes();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> {}).build();
        getSystemService(AudioManager.class).requestAudioFocus(focus);
        if (a.optBoolean("vibrate",true)) {
            vibrator = getSystemService(Vibrator.class);
            if (vibrator != null && vibrator.hasVibrator())
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,500,900,500,1400},0),attributes);
        }
        startPlayer(a,generation,false);
        main.postDelayed(() -> { if (sequence == generation) reconcile(); },until+30);
        sendBroadcast(new Intent(STATE).setPackage(getPackageName()));
    }

    private static AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    }
    private void startPlayer(JSONObject a,int generation,boolean backup) {
        if (sequence!=generation) return;
        releasePlayer();
        String tone = backup ? "nox" : a.optString("tone","nox");
        Uri uri;
        if ("system".equals(tone)) uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        else if (tone.startsWith("content://")) uri=Uri.parse(tone);
        else {
            int raw = "dawn".equals(tone)?R.raw.dawn:R.raw.nox;
            uri=Uri.parse("android.resource://"+getPackageName()+"/"+raw);
        }
        MediaPlayer p = new MediaPlayer();
        player=p;
        float level=Math.max(1,Math.min(100,a.optInt("volume",80)))/100f;
        Runnable fallback = () -> {
            if (sequence!=generation || player!=p) return;
            releasePlayer();
            if (!backup) startPlayer(a,generation,true);
            else beep(generation,Math.max(1,Math.round(level*100)));
        };
        try {
            p.setAudioAttributes(attributes());
            p.setDataSource(this,uri);
            p.setLooping(true);
            p.setOnErrorListener((mp,what,extra) -> { main.post(fallback); return true; });
            p.setOnPreparedListener(mp -> {
                if (sequence!=generation || player!=p) return;
                main.removeCallbacks(fallback);
                boolean ramp=a.optBoolean("ramp",true);
                float initial=ramp?level*0.15f:level;
                try {
                    p.setVolume(initial,initial); p.start();
                    if (ramp) ramp(p,generation,level,SystemClock.elapsedRealtime());
                } catch (RuntimeException e) { main.post(fallback); }
            });
            p.prepareAsync();
            main.postDelayed(fallback,4500);
        } catch (Exception e) { main.post(fallback); }
    }
    private void ramp(MediaPlayer p,int generation,float level,long start) {
        if (player!=p || sequence!=generation) return;
        float fraction=Math.min(1f,(SystemClock.elapsedRealtime()-start)/30000f);
        float value=level*(0.15f+0.85f*fraction);
        try { p.setVolume(value,value); } catch (RuntimeException ignored) { return; }
        if (fraction < 1f) main.postDelayed(()->ramp(p,generation,level,start),500);
    }
    private void beep(int generation,int level) {
        if (sequence != generation) return;
        try {
            if (emergencyTone==null) emergencyTone=new ToneGenerator(AudioManager.STREAM_ALARM,level);
            emergencyTone.startTone(ToneGenerator.TONE_PROP_BEEP2,600);
            main.postDelayed(()->beep(generation,level),1500);
        } catch (RuntimeException e) { Log.w("Nox","Saída de áudio indisponível",e); }
    }
    private void releasePlayer() {
        if (player!=null) {
            player.setOnPreparedListener(null);
            player.setOnErrorListener(null);
            player.release();
            player=null;
        }
    }
    private void stopSound() {
        sequence++;
        main.removeCallbacksAndMessages(null);
        releasePlayer();
        if (emergencyTone!=null) { emergencyTone.release(); emergencyTone=null; }
        if (vibrator!=null) { vibrator.cancel(); vibrator=null; }
        if (focus!=null) { getSystemService(AudioManager.class).abandonAudioFocusRequest(focus); focus=null; }
        if (awake!=null && awake.isHeld()) awake.release();
        current=""; token=0;
    }
    @Override public void onDestroy() {
        if (live==this) live=null;
        stopSound();
        AlarmReceiver.release();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
