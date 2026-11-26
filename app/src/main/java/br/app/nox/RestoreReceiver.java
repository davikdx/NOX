package br.app.nox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action==null) return;
        try { Scheduler.restore(context,action); }
        catch (RuntimeException e) { Log.w("Nox","Falha na restauração dos alarmes",e); }
        try { ReminderReceiver.sync(context); }
        catch (RuntimeException e) { Log.w("Nox","Falha na restauração dos lembretes",e); }
    }
}
