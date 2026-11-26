package br.app.nox;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

/** Private, credential-encrypted database; one SQLiteOpenHelper per process. All components
 * run in the default process. No destructive fallback on schema downgrades. */
public final class Store {
    static final String[] BACKUP_TYPES = {"setting", "alarm", "night", "day"};
    private static Helper shared;
    private final Helper helper;

    public Store(Context context) {
        synchronized (Store.class) {
            if (shared == null) shared = new Helper(context.getApplicationContext());
            helper = shared;
        }
    }

    private static class Helper extends SQLiteOpenHelper {
        Helper(Context c) { super(c, "nox.db", null, 2); }
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE records (kind TEXT NOT NULL, id TEXT NOT NULL, data TEXT NOT NULL, PRIMARY KEY(kind,id))");
            onUpgrade(db, 1, 2);
        }
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion == 1 && newVersion >= 2) {
                db.execSQL("ALTER TABLE records ADD COLUMN updated INTEGER NOT NULL DEFAULT 0");
                db.execSQL("CREATE INDEX records_recent ON records(kind,updated)");
                oldVersion = 2;
            }
            if (oldVersion != newVersion) throw new IllegalStateException("Migração indisponível");
        }
    }

    private static JSONObject decode(String source) {
        try { return new JSONObject(source); }
        catch (JSONException e) { throw new IllegalStateException("Registro local ilegível", e); }
    }

    public JSONObject get(String kind, String id) {
        if (id == null) return null;
        try (Cursor c = helper.getReadableDatabase().query("records", new String[]{"data"},
                "kind=? AND id=?", new String[]{kind,id}, null,null,null,"1")) {
            return c.moveToFirst() ? decode(c.getString(0)) : null;
        }
    }

    public List<JSONObject> all(String kind) {
        List<JSONObject> rows = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query("records", new String[]{"data"},
                "kind=?", new String[]{kind},null,null,"updated DESC, id")) {
            while (c.moveToNext()) rows.add(decode(c.getString(0)));
        }
        return rows;
    }

    public void put(String kind, JSONObject value) {
        String id = value.optString("id", "");
        if (id.trim().isEmpty()) throw new IllegalArgumentException("Registro sem identificador");
        ContentValues row = new ContentValues();
        row.put("kind",kind);
        row.put("id",id);
        row.put("data",value.toString());
        row.put("updated",System.currentTimeMillis());
        long result = helper.getWritableDatabase().insertWithOnConflict("records", null, row, SQLiteDatabase.CONFLICT_REPLACE);
        if (result == -1) throw new android.database.SQLException("Falha ao gravar registro");
    }

    public void remove(String kind, String id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        if (!"night".equals(kind)) {
            db.delete("records","kind=? AND id=?",new String[]{kind,id});
            return;
        }
        db.beginTransaction();
        try {
            for (JSONObject day : all("day")) {
                if (id.equals(day.optString("night"))) remove("day",day.optString("id"));
            }
            for (JSONObject alarm : all("alarm")) {
                if (id.equals(alarm.optString("night"))) {
                    alarm.remove("night");
                    put("alarm", alarm);
                }
            }
            db.delete("records","kind=? AND id=?",new String[]{kind,id});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    void clearHistory() {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("records","kind IN ('night','day')",null);
            for (JSONObject alarm : all("alarm")) {
                if (alarm.has("night")) {
                    alarm.remove("night");
                    put("alarm",alarm);
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    JSONObject exportData() {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            JSONObject data = J.obj("format","br.app.nox.backup","version",1,"exported",System.currentTimeMillis());
            for (String kind : BACKUP_TYPES) J.put(data,kind,new JSONArray(all(kind)));
            db.setTransactionSuccessful();
            return data;
        } finally { db.endTransaction(); }
    }

    void replaceData(JSONObject backup) throws JSONException {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("records",null,null);
            for (String kind : BACKUP_TYPES) {
                JSONArray rows = backup.getJSONArray(kind);
                for (int i = 0; i < rows.length(); i++) put(kind,rows.getJSONObject(i));
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}

