package br.app.nox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;
import java.util.List;

public final class AlarmReceiver extends BroadcastReceiver {
    private static PowerManager.WakeLock handoff;

    @Override public void onReceive(Context context, Intent intent) {
        if (!Scheduler.FIRE.equals(intent.getAction()) || intent.getData()==null) return;
        List<String> path = intent.getData().getPathSegments();
        if (path.size()!=2) return;
        try {
            if (!Scheduler.fired(context,path.get(0),Long.parseLong(path.get(1)))) return;
            acquire(context);
            context.startForegroundService(new Intent(context,RingService.class));
        } catch (RuntimeException e) {
            release();
            Log.e("Nox","Falha ao iniciar o alarme",e);
            RingService.notifyFallback(context);
        }
    }

    private static synchronized void acquire(Context c) {
        if (handoff==null) {
            handoff = c.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Nox:entrega");
            handoff.setReferenceCounted(false);
        }
        handoff.acquire(15000);
    }

    static synchronized void release() {
        if (handoff!=null && handoff.isHeld()) handoff.release();
    }
}
