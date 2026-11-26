package br.app.nox;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;
import java.time.*;

/** Two opt-in, inexact reminders that never start the alarm service. */
public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL="nox_routine";
    private static int id(String kind){return "bed".equals(kind)?7110:7111;}
    private static PendingIntent pending(Context c,String kind){
        Intent i=new Intent(c,ReminderReceiver.class).setAction("br.app.nox.reminder."+kind).putExtra("kind",kind);
        return PendingIntent.getBroadcast(c,id(kind),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void sync(Context c) {
        JSONObject cfg=new Store(c).get("setting","main");
        AlarmManager am=c.getSystemService(AlarmManager.class);
        for(String kind:new String[]{"bed","day"}){
            PendingIntent pi=pending(c,kind);
            if(cfg==null || !cfg.optBoolean(kind+"Reminder",false)){am.cancel(pi);continue;}
            int def="bed".equals(kind)?cfg.optInt("bedMinute",1350):840;
            int minute=Math.max(0,Math.min(1439,cfg.optInt(kind+"ReminderMinute",def)));
            long at=Sleep.nextTime(System.currentTimeMillis(),minute,ZoneId.systemDefault());
            am.setWindow(AlarmManager.RTC,at,15*Sleep.MIN,pi);
        }
    }
    @Override public void onReceive(Context c,Intent i) {
        String kind=i.getStringExtra("kind");
        if(!"bed".equals(kind)&&!"day".equals(kind))return;
        Store s=new Store(c);
        JSONObject cfg=s.get("setting","main");
        if(cfg==null || !cfg.optBoolean(kind+"Reminder",false))return;
        // Re-arm only this kind, so a simultaneous due reminder keeps its own PendingIntent.
        long now=System.currentTimeMillis();
        int minute=Math.max(0,Math.min(1439,cfg.optInt(kind+"ReminderMinute","bed".equals(kind)?cfg.optInt("bedMinute",1350):840)));
        long at=Sleep.nextTime(now,minute,ZoneId.systemDefault());
        c.getSystemService(AlarmManager.class).setWindow(AlarmManager.RTC,at,15*Sleep.MIN,pending(c,kind));
        String today=LocalDate.now().toString(), key="lastReminder"+kind;
        if(today.equals(cfg.optString(key)))return;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(!nm.areNotificationsEnabled())return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Lembretes da rotina",NotificationManager.IMPORTANCE_DEFAULT));
        boolean bed="bed".equals(kind);
        Intent open=new Intent(c,MainActivity.class).putExtra("screen",bed?"sleep":"day")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(c,id(kind),open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(bed?"Hora de preparar seu sono":"Como está sua sonolência?")
            .setContentText(bed?"Abra sua rotina noturna no Nox.":"Toque para registrar como você se sente.")
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(content).setAutoCancel(true).build();
        try{nm.notify(id(kind),n);J.put(cfg,key,today);s.put("setting",cfg);}catch(SecurityException denied){/* Permission changed after the check. */}
    }
}
