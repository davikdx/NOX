package br.app.nox;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Each scheduled system event has its own (alarm id, instant) identity. */
public final class Scheduler {
    static final String ALARM = "alarm";
    static final String FIRE = "br.app.nox.FIRE";
    static final long RING_WINDOW = 15 * AlarmTimes.MINUTE;
    private static final String[] TRANSIENT = {"_armed","_token","_since","_base","_count","_snoozing","_wallDate","_firedDate","_skipDate","_ringLog","_snoozeLog","_firstActual"};
    private Scheduler() {}

    public static boolean exact(Context c) {
        AlarmManager manager = c.getSystemService(AlarmManager.class);
        return manager != null && (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms());
    }

    private static PendingIntent operation(Context c, String id, long at, boolean find) {
        Uri key = new Uri.Builder().scheme("nox").authority("alarm").appendPath(id).appendPath(String.valueOf(at)).build();
        Intent i = new Intent(c, AlarmReceiver.class).setAction(FIRE).setData(key);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_IMMUTABLE |
            (find ? PendingIntent.FLAG_NO_CREATE : PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private static void cancel(Context c, String id, long at) {
        if (at <= 0) return;
        PendingIntent op = operation(c,id,at,true);
        if (op != null) {
            c.getSystemService(AlarmManager.class).cancel(op);
            op.cancel();
        }
    }

    public static synchronized void cancelSystem(Context c, String id) {
        JSONObject old = new Store(c).get(ALARM,id);
        if (old != null) cancel(c,id,old.optLong("_armed"));
    }

    private static boolean absolute(JSONObject a) {
        return a.optLong("deadline") > 0 || !a.optString("night","").isEmpty() || a.optBoolean("_test");
    }

    private static void clearState(JSONObject a) {
        for (String field : TRANSIENT) a.remove(field);
    }

    private static long civil(JSONObject a,long now,String omit) {
        return AlarmTimes.next(a.optInt("minute",-1),a.optInt("days"),now,ZoneId.systemDefault(),omit);
    }

    private static void saveAndArm(Context c, Store db, JSONObject a, long at, long oldAt) {
        if (!exact(c)) throw new IllegalStateException("Permita alarmes exatos em Confiabilidade do despertador.");
        String id = a.optString("id");
        PendingIntent op = operation(c,id,at,false);
        Intent show = new Intent(c,MainActivity.class).setAction(Intent.ACTION_VIEW)
            .setData(new Uri.Builder().scheme("nox").authority("show").appendPath(id).build())
            .putExtra("screen","alarm");
        PendingIntent info = PendingIntent.getActivity(c,0,show,PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            c.getSystemService(AlarmManager.class).setAlarmClock(new AlarmManager.AlarmClockInfo(at,info),op);
            J.put(a,"at",at);
            J.put(a,"_armed",at);
            if (!a.optBoolean("_snoozing")) J.put(a,"_wallDate",AlarmTimes.date(at));
            db.put(ALARM,a);
        } catch (RuntimeException e) {
            if (at != oldAt) cancel(c,id,at);
            throw e;
        }
        if (oldAt != at) cancel(c,id,oldAt);
    }

    public static synchronized void put(Context c, JSONObject input) {
        Store db = new Store(c);
        JSONObject a = J.copy(input);
        String id = a.optString("id","");
        if (id.isEmpty()) throw new IllegalArgumentException("O alarme precisa de um identificador.");
        JSONObject old = db.get(ALARM,id);
        long oldAt = old == null ? 0 : old.optLong("_armed");
        clearState(a);
        if (!a.optBoolean("enabled",true)) {
            a.remove("at");
            db.put(ALARM,a);
            cancel(c,id,oldAt);
        } else {
            J.put(a,"enabled",true);
            long now = System.currentTimeMillis();
            long at = absolute(a) ? a.optLong("at") : civil(a,now,"");
            if (at <= now) throw new IllegalArgumentException("Escolha um horário de despertar futuro.");
            if (a.optLong("deadline") > 0 && at > a.optLong("deadline"))
                throw new IllegalArgumentException("O despertar ultrapassa o horário-limite.");
            saveAndArm(c,db,a,at,oldAt);
        }
        if (old != null && old.optLong("_token") > 0) RingService.refresh(c);
    }

    public static synchronized void remove(Context c,String id) {
        Store db = new Store(c);
        JSONObject old = db.get(ALARM,id);
        if (old == null) return;
        db.remove(ALARM,id);
        cancel(c,id,old.optLong("_armed"));
        if (old.optLong("_token") > 0) RingService.refresh(c);
    }

    public static synchronized void skip(Context c,String id) {
        Store db = new Store(c);
        JSONObject a = db.get(ALARM,id);
        if (a == null || !a.optBoolean("enabled") || a.optInt("days") == 0 || a.optLong("_token") > 0)
            throw new IllegalStateException("Só é possível pular um alarme recorrente agendado.");
        if (a.optBoolean("_snoozing")) throw new IllegalStateException("Encerre a soneca em andamento.");
        long after = Math.max(a.optLong("at"),System.currentTimeMillis());
        String date = AlarmTimes.date(after);
        long next = civil(a,after,date);
        J.put(a,"_skipDate",date);
        saveAndArm(c,db,a,next,a.optLong("_armed"));
    }

    public static synchronized JSONObject next(Context c) {
        if (!exact(c)) return null;
        JSONObject result = null;
        long now = System.currentTimeMillis();
        for (JSONObject a : new Store(c).all(ALARM)) {
            long at = a.optLong("at");
            if (!a.optBoolean("enabled") || at < now || a.optLong("_armed") != at) continue;
            if (operation(c,a.optString("id"),at,true) == null) continue;
            if (result == null || at < result.optLong("at")) result = a;
        }
        return result;
    }

    public static void test(Context c) {
        put(c,J.obj("id","nox-test","name","Teste do despertador","at",System.currentTimeMillis()+30000,
            "enabled",true,"_test",true,"tone","nox","volume",75,"vibrate",true,"snooze",5));
    }

    public static synchronized JSONObject ringing(Context c) {
        JSONObject first = null;
        for (JSONObject a : new Store(c).all(ALARM)) {
            long token = a.optLong("_token");
            if (token == 0) continue;
            if (first == null || a.optLong("_since") < first.optLong("_since")
                || (a.optLong("_since")==first.optLong("_since") && a.optString("id").compareTo(first.optString("id")) < 0)) first = a;
        }
        return first;
    }

    static JSONArray array(JSONObject o, String name) {
        JSONArray a = o.optJSONArray(name);
        if (a == null) { a = new JSONArray(); J.put(o,name,a); }
        return a;
    }

    static synchronized boolean fired(Context c,String id,long token) {
        Store db = new Store(c);
        JSONObject a = db.get(ALARM,id);
        if (a == null || !a.optBoolean("enabled") || a.optLong("_armed") != token || token <= 0) return false;
        long now = System.currentTimeMillis();
        if (now < token - 1000) { saveAndArm(c,db,a,token,token); return false; }
        long cutoff = a.optLong("deadline");
        if (now - token > RING_WINDOW && (cutoff <= 0 || now > cutoff)) {
            finish(c,db,a,now);
            return false;
        }
        if (!a.optBoolean("_snoozing")) {
            J.put(a,"_base",token);
            J.put(a,"_firedDate",AlarmTimes.date(token));
            J.put(a,"_count",0);
            J.put(a,"_firstActual",now);
            a.remove("_ringLog"); a.remove("_snoozeLog");
        }
        a.remove("_armed");
        a.remove("_snoozing");
        J.put(a,"_token",token);
        J.put(a,"_since",now);
        array(a,"_ringLog").put(now);
        db.put(ALARM,a);
        cancel(c,id,token);
        JSONObject night = night(db,a);
        if (night != null) {
            if (night.optLong("firstRing") == 0) J.put(night,"firstRing",now);
            array(night,"rings").put(now);
            db.put("night",night);
        }
        return true;
    }

    static long snoozeTarget(JSONObject a,long now) {
        if (a == null || a.optLong("_token") <= 0) return 0;
        return AlarmTimes.snooze(now,a.optInt("snooze",5),a.optLong("deadline"),a.optInt("_count"),a.optInt("maxSnoozes"));
    }

    static synchronized boolean snooze(Context c,String id,long token) {
        Store db = new Store(c);
        JSONObject a = db.get(ALARM,id);
        if (a == null || a.optLong("_token") != token) return false;
        long now = System.currentTimeMillis();
        long target = snoozeTarget(a,now);
        if (target == 0) return false;
        a.remove("_token");
        a.remove("_since");
        J.put(a,"_snoozing",true);
        J.put(a,"_count",a.optInt("_count")+1);
        double duration = (target-now)/60000d;
        try { array(a,"_snoozeLog").put(duration); }
        catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        saveAndArm(c,db,a,target,0);
        JSONObject night = night(db,a);
        if (night != null) {
            try { array(night,"snoozes").put(duration); }
            catch (org.json.JSONException e) { throw new IllegalStateException(e); }
            db.put("night",night);
        }
        return true;
    }

    private static JSONObject night(Store db,JSONObject a) {
        String id = a.optString("night","");
        return id.isEmpty() ? null : db.get("night",id);
    }

    static synchronized String dismiss(Context c,String id,long token,String action) {
        Store db = new Store(c);
        JSONObject a = db.get(ALARM,id);
        if (a == null || a.optLong("_token") != token || token == 0) return null;
        long now = System.currentTimeMillis();
        JSONObject n = night(db,a);
        boolean up = RingService.UP.equals(action);
        boolean awake = up || RingService.WAKE.equals(action);
        if (awake && n == null && !a.optBoolean("_test")) {
            // A declared awakening is a valid partial entry. Never infer bed or onset.
            n = J.obj("id",UUID.randomUUID().toString(),"firstRing",a.optLong("_firstActual",a.optLong("_since")),
                "rings",a.optJSONArray("_ringLog")==null?new JSONArray():a.optJSONArray("_ringLog"),
                "snoozes",a.optJSONArray("_snoozeLog")==null?new JSONArray():a.optJSONArray("_snoozeLog"));
        }
        if (awake && n != null) {
            if (n.optLong("wake") == 0) J.put(n,"wake",now);
            if (up && n.optLong("up") == 0) J.put(n,"up",now);
            db.put("night",n);
        }
        if (!awake && a.optLong("deadline") > now && exact(c)) {
            // An unanswered early ring keeps a second chance at the user's limit.
            a.remove("_token");
            a.remove("_since");
            J.put(a,"_snoozing",true);
            saveAndArm(c,db,a,a.optLong("deadline"),0);
        } else {
            finish(c,db,a,now);
        }
        return n == null ? "" : n.optString("id");
    }

    private static void finish(Context c,Store db,JSONObject a,long now) {
        long armed = a.optLong("_armed");
        String last = a.optString("_firedDate");
        clearState(a);
        a.remove("at");
        cancel(c,a.optString("id"),armed);
        if (a.optBoolean("_test")) { db.remove(ALARM,a.optString("id")); return; }
        if (a.optInt("days") != 0 && !absolute(a)) {
            long at = civil(a,now,last);
            J.put(a,"at",at);
            J.put(a,"_firedDate",last);
            db.put(ALARM,a);
            if (exact(c)) {
                try { saveAndArm(c,db,a,at,0); }
                catch (RuntimeException e) { Log.w("Nox","Recorrência pendente de reagendamento",e); }
            }
        } else {
            J.put(a,"enabled",false);
            db.put(ALARM,a);
        }
    }

    public static synchronized void sync(Context c) { restore(c,""); }

    static synchronized void restore(Context c,String reason) {
        Store db = new Store(c);
        long now = System.currentTimeMillis();
        boolean wallChange = Intent.ACTION_TIMEZONE_CHANGED.equals(reason) || Intent.ACTION_TIME_CHANGED.equals(reason);
        boolean restart = Intent.ACTION_BOOT_COMPLETED.equals(reason) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(reason);
        for (JSONObject a : db.all(ALARM)) {
            if (!a.optBoolean("enabled")) continue;
            try {
                long token = a.optLong("_token");
                if (token > 0) {
                    if (now - a.optLong("_since") >= RING_WINDOW) {
                        dismiss(c,a.optString("id"),token,"timeout");
                        continue;
                    }
                    if (!restart) continue;
                    // Boot receivers only re-arm. Playback starts from the exact alarm.
                    a.remove("_token"); a.remove("_since");
                    J.put(a,"_snoozing",true);
                    saveAndArm(c,db,a,now+1500,0);
                    continue;
                }
                if (!exact(c)) continue;
                long at = a.optLong("at"), old = a.optLong("_armed");
                boolean civil = !absolute(a) && !a.optBoolean("_snoozing");
                if (civil && (at == 0 || wallChange)) {
                    String date = a.optString("_wallDate","");
                    if (a.optInt("days") == 0 && !date.isEmpty()) {
                        at = AlarmTimes.onDate(LocalDate.parse(date),a.optInt("minute"),ZoneId.systemDefault(),0);
                    } else {
                        String omit = a.optString("_skipDate",a.optString("_firedDate",""));
                        at = civil(a,now,omit);
                    }
                }
                if (at == 0 || at < now - RING_WINDOW) { finish(c,db,a,now); continue; }
                if (reason.isEmpty() && at == old && operation(c,a.optString("id"),at,true) != null) continue;
                saveAndArm(c,db,a,at,old);
            } catch (RuntimeException e) { Log.w("Nox","Não foi possível restaurar o despertador",e); }
        }
    }
}
