package br.app.nox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** JSON backups use the user's document picker. Import is validated before replacement. */
final class DataActions {
    static final int SAVE=710, OPEN=711;
    private final MainActivity m;
    private static final ExecutorService io=Executors.newSingleThreadExecutor();
    private static final int MAX_BYTES=32*1024*1024;
    DataActions(MainActivity m) { this.m=m; }

    void backup() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE,"Nox-backup-"+LocalDate.now()+".json");
        m.startActivityForResult(i,SAVE);
    }
    void restore() {
        m.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),OPEN);
    }

    void result(int request,int result,Intent data) {
        if ((request!=SAVE&&request!=OPEN) || result!=Activity.RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        io.execute(()->{
            try {
                if(request==SAVE) {
                    JSONObject snapshot=m.store.exportData();
                    stripTransient(snapshot,false);
                    try(OutputStream out=m.getContentResolver().openOutputStream(uri,"wt")) {
                        if(out==null) throw new IOException("O destino não aceitou o arquivo.");
                        out.write(snapshot.toString(1).getBytes(StandardCharsets.UTF_8));
                    }
                    ui(()->m.message("Backup salvo no local escolhido."));
                } else {
                    JSONObject imported;
                    try(InputStream in=m.getContentResolver().openInputStream(uri)) {
                        if(in==null) throw new IOException("Arquivo indisponível.");
                        imported=readBackup(in);
                    }
                    ui(()->confirm(imported));
                }
            } catch(Exception e){ui(()->m.message("Falha no backup: "+reason(e)));}
        });
    }

    private void ui(Runnable r) {
        m.runOnUiThread(()->{if(!m.isFinishing()&&!m.isDestroyed())r.run();});
    }
    private static String reason(Exception e) {return e.getMessage()==null?"arquivo ou destino indisponível.":e.getMessage();}

    static JSONObject readBackup(InputStream in) throws IOException,JSONException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192]; int n;
        while((n=in.read(buffer))!=-1) {
            if(bytes.size()+n>MAX_BYTES) throw new IOException("O arquivo excede 32 MiB.");
            bytes.write(buffer,0,n);
        }
        JSONObject backup=new JSONObject(bytes.toString("UTF-8"));
        if(!"br.app.nox.backup".equals(backup.optString("format"))||backup.getInt("version")!=1)
            throw new JSONException("Formato ou versão incompatível.");
        for(String kind:Store.BACKUP_TYPES) {
            JSONArray list=backup.getJSONArray(kind);
            Set<String> ids=new HashSet<>();
            for(int i=0;i<list.length();i++){
                JSONObject row=list.getJSONObject(i);
                String id=row.getString("id");
                if(id.length()>160||id.trim().isEmpty()||!ids.add(id))
                    throw new JSONException("Identificador inválido ou repetido em "+kind+".");
                if("alarm".equals(kind)||"setting".equals(kind)) {
                    bounds(row,"minute",0,1439);bounds(row,"days",0,127);
                    bounds(row,"volume",1,100);bounds(row,"snooze",1,120);
                    bounds(row,"maxSnoozes",0,100);bounds(row,"goal",1,1440);
                    bounds(row,"latency",0,720);bounds(row,"wakeMinute",0,1439);
                    bounds(row,"bedMinute",0,1439);bounds(row,"window",0,60);
                    if("alarm".equals(kind)) {
                        if(!row.has("enabled"))throw new JSONException("Alarme sem estado.");
                        if(row.optInt("days",0)!=0&&!row.has("minute"))throw new JSONException("Alarme recorrente sem horário.");
                        if(row.optLong("deadline")>0 && row.optLong("at")>row.optLong("deadline"))
                            throw new JSONException("Alarme posterior ao limite.");
                    }
                    String tone=row.optString("tone","nox");
                    if(!Arrays.asList("nox","dawn","system").contains(tone)&&!tone.startsWith("content://"))
                        throw new JSONException("Referência de áudio incompatível.");
                } else if("night".equals(kind)) {
                    bounds(row,"quality",1,5);bounds(row,"fatigue",0,4);bounds(row,"headache",0,3);
                    bounds(row,"awakenings",-1,4);bounds(row,"waso",0,1440);
                    bounds(row,"reportedLatency",0,1440);
                    long bed=row.optLong("bed"),wake=row.optLong("wake"),up=row.optLong("up");
                    if((wake>0&&bed>0&&wake<bed)||(up>0&&wake>0&&up<wake))
                        throw new JSONException("Sequência de horários inválida em uma noite.");
                } else {
                    bounds(row,"level",1,5);
                    if(row.optLong("at")<=0||!row.has("level")) throw new JSONException("Avaliação diurna incompleta.");
                }
            }
        }
        stripTransient(backup,true);
        return backup;
    }

    private static void bounds(JSONObject o,String key,int low,int high) throws JSONException {
        if(o.isNull(key))return;
        Object raw=o.get(key);
        if(!(raw instanceof Number))throw new JSONException("Valor não numérico em "+key+".");
        double value=((Number)raw).doubleValue();
        if(!Double.isFinite(value)||value!=Math.rint(value)||value<low||value>high)
            throw new JSONException("Valor fora da faixa em "+key+".");
    }

    static void stripTransient(JSONObject snapshot,boolean restoring) throws JSONException {
        JSONArray alarms=snapshot.getJSONArray("alarm");
        long now=System.currentTimeMillis();
        for(int i=alarms.length()-1;i>=0;i--){
            JSONObject row=alarms.getJSONObject(i);
            if(row.optBoolean("_test")){alarms.remove(i);continue;}
            boolean ringing=row.optLong("_token")>0;
            for(Iterator<String> it=row.keys();it.hasNext();) if(it.next().startsWith("_"))it.remove();
            if((ringing&&row.optInt("days")==0)||(restoring&&row.optLong("at")>0&&row.optLong("at")<=now&&row.optInt("days")==0))
                J.put(row,"enabled",false);
            if(restoring&&row.optInt("days")!=0)row.remove("at");
        }
    }

    private void confirm(JSONObject imported) {
        int nights=imported.optJSONArray("night").length(), alarms=imported.optJSONArray("alarm").length();
        String details=nights+" noite(s), "+alarms+" alarme(s) e "+imported.optJSONArray("day").length()
            +" avaliação(ões) diurna(s). O conteúdo atual será substituído. Músicas externas podem precisar ser escolhidas novamente.";
        new AlertDialog.Builder(m).setTitle("Restaurar este backup?").setMessage(details)
            .setNegativeButton("Cancelar",null).setPositiveButton("Substituir e restaurar",(d,w)->{
                try {
                    synchronized(Scheduler.class) {
                        if(Scheduler.ringing(m)!=null){m.message("Encerre o alarme que está tocando antes de restaurar.");return;}
                        try {
                            for(JSONObject row:m.store.all("alarm")) Scheduler.cancelSystem(m,row.optString("id"));
                            m.store.replaceData(imported);
                        } finally { Scheduler.sync(m); }
                    }
                    ReminderReceiver.sync(m);
                    m.message(Scheduler.exact(m)?"Backup restaurado. Confira os próximos horários.":"Backup restaurado. Autorize alarmes exatos para reativar os horários.");
                    m.recreate();
                } catch(Exception e){m.message("Falha ao restaurar: "+reason(e));}
            }).show();
    }
}

