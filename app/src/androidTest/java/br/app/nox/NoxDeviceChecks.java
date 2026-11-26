package br.app.nox;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Device-only instrumented checks. Never opens the release package or a populated database. */
public final class NoxDeviceChecks extends Instrumentation {
    private static final String DEV = "br.app.nox.dev";
    private static final String QA_NIGHT = "qa-nox-instrumentation-night";
    private static final String QA_ALARM = "qa-nox-instrumentation-alarm";
    private boolean withAlarm, reboot;
    private MainActivity activity;
    private Context target;
    private Store store;
    private File out;
    private final StringBuilder log = new StringBuilder();

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        withAlarm = arguments != null && "1".equals(arguments.getString("alarm"));
        reboot = arguments != null && "1".equals(arguments.getString("reboot"));
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        boolean cleanProfile = false;
        Throwable problem = null;
        try {
            target = getTargetContext();
            require(DEV.equals(target.getPackageName()), "Somente o pacote de desenvolvimento pode ser testado.");
            store = new Store(target);
            for (String kind : Store.BACKUP_TYPES) {
                require(store.all(kind).isEmpty(), "Este perfil já contém registros. Use um emulador de QA vazio.");
            }
            if (reboot) {
                long trigger = System.currentTimeMillis() + 150000;
                Scheduler.put(target, J.obj("id","qa-reboot","name","Teste após reiniciar","enabled",true,
                    "at",trigger,"deadline",trigger,"tone","nox","volume",15,"vibrate",false));
                require(Scheduler.next(target)!=null,"Agendamento pré-reinício ausente.");
                passed("Alarme armado antes do reinício");
            } else {
            cleanProfile = true;
            out = new File(target.getFilesDir(), "qa-nox");
            require(out.isDirectory() || out.mkdirs(), "Não foi possível criar a pasta de evidências.");
            screenChecks("dark", "escuro");
            screenChecks("light", "claro");
            backupCheck();
            ReportChecks.run(target);
            passed("Histórico e PDF multipágina");
            if (withAlarm) {
                store.put("setting", J.obj("id","main","theme","dark","onboarded",true));
                alarmCheck();
            } else {
                log.append("PENDENTE: alarme real (execute com NOX_TEST_ALARM=1).\n");
            }
            }
        } catch (Throwable failure) {
            problem = failure;
            log.append(android.util.Log.getStackTraceString(failure));
        } finally {
            if (cleanProfile) {
                try {
                    if (store.get("alarm", QA_ALARM) != null) onUi(() -> { Scheduler.remove(target,QA_ALARM); return null; });
                    store.remove("night", QA_NIGHT);
                    store.remove("setting", "main");
                    finishScreen();
                    for (String kind : Store.BACKUP_TYPES) require(store.all(kind).isEmpty(),
                        "Restaram registros de teste em " + kind);
                } catch (Throwable cleanup) {
                    if (problem == null) problem = cleanup;
                    log.append("\nLimpeza: ").append(android.util.Log.getStackTraceString(cleanup));
                }
            }
            String status = problem == null ? (reboot ? "ARMED" : "PASS") : "FAIL";
            result.putString("nox_result",status);
            result.putString("stream","\n" + log + "\n" + status + "\n");
            finish(problem == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        }
    }

    private void passed(String message) {
        log.append("OK: ").append(message).append('\n');
        Bundle state = new Bundle();
        state.putString("stream", "OK: " + message + "\n");
        sendStatus(1, state);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void waitFor(String what, long timeout, BooleanSupplier condition) {
        long until = SystemClock.elapsedRealtime() + timeout;
        do {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < until);
        throw new AssertionError("Tempo esgotado: " + what);
    }

    private <T> T onUi(Supplier<T> work) {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        runOnMainSync(() -> {
            try { value.set(work.get()); }
            catch (Throwable t) { error.set(t); }
        });
        if (error.get() != null) throw new AssertionError("Falha na interface", error.get());
        return value.get();
    }

    private void finishScreen() {
        if (activity == null) return;
        onUi(() -> { activity.finish(); return null; });
        waitForIdleSync();
        activity = null;
    }

    private static View navigation(View v, String description) {
        if (description.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())
                && v.isClickable()) return v;
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)v;
            for (int i = 0; i < group.getChildCount(); i++) {
                View hit = navigation(group.getChildAt(i), description);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static boolean hasTitle(View view, String value) {
        if (view == null) return false;
        if (view instanceof TextView && ((TextView)view).getText().toString().equals(value)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0; i<group.getChildCount(); i++) if (hasTitle(group.getChildAt(i),value)) return true;
        }
        return false;
    }

    private void screenChecks(String theme,String suffix) throws Exception {
        finishScreen();
        store.put("setting", J.obj("id","main","theme",theme,"onboarded",true));
        activity = (MainActivity)startActivitySync(new Intent(target,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitForIdleSync();
        waitFor("janela com foco após iniciar a Activity",18000,
            () -> onUi(() -> activity.hasWindowFocus() && activity.getWindow().getDecorView().isLaidOut()));
        boolean dark = onUi(() -> android.graphics.Color.luminance(activity.bg) < 0.5f);
        require(dark == theme.equals("dark"), "Tema incorreto: " + theme);
        String[] tabs = {"Início","Alarmes","Sono","Histórico"};
        String[] names = {"inicio","alarmes","sono","historico"};
        for (int i=0; i<tabs.length; i++) {
            String tab = tabs[i];
            onUi(() -> {
                View button = navigation(activity.getWindow().getDecorView(),tab);
                require(button != null && button.performClick(), "Aba indisponível: " + tab);
                View selected = navigation(activity.getWindow().getDecorView(),tab);
                require(selected != null && selected.isSelected(), "Aba não selecionada: " + tab);
                if (!"Início".equals(tab)) require(hasTitle(activity.body,tab), "Tela incorreta: " + tab);
                else require(hasTitle(activity.body,"Sem alarme ativo"), "Estado inicial inesperado.");
                return null;
            });
            waitForIdleSync();
            capture(names[i] + "-" + suffix);
        }
        passed("Navegação nas quatro abas, tema " + suffix + " e capturas do dispositivo");
    }

    private boolean targetInFront() {
        AccessibilityNodeInfo active = getUiAutomation().getRootInActiveWindow();
        if (active == null) return false;
        try { return DEV.contentEquals(active.getPackageName() == null ? "" : active.getPackageName()); }
        finally { active.recycle(); }
    }

    private void capture(String name) throws Exception {
        waitFor("janela do Nox em primeiro plano",6000,this::targetInFront);
        // Allow the first frame after layout to reach SurfaceFlinger.
        getUiAutomation().waitForIdle(200,4000);
        require(targetInFront(), "A captura exige uma janela visível do Nox.");
        Bitmap b = getUiAutomation().takeScreenshot();
        require(b != null && b.getWidth() > 0 && b.getHeight() > 0, "Captura indisponível.");
        try (FileOutputStream file = new FileOutputStream(new File(out,name+".png"))) {
            if (name.endsWith("-escuro") || name.equals("alarme-tocando")) {
                Bitmap pixels = b.getConfig()==Bitmap.Config.HARDWARE ? b.copy(Bitmap.Config.ARGB_8888,false) : b;
                int black=0, samples=0;
                for (int y=b.getHeight()/12; y<b.getHeight()*11/12; y+=12) {
                    for (int x=4; x<b.getWidth(); x+=12) {
                        if (pixels.getPixel(x,y)==android.graphics.Color.BLACK) black++;
                        samples++;
                    }
                }
                if (pixels!=b) pixels.recycle();
                require(black > samples*0.60, "Fundo AMOLED ausente em "+name);
                passed(name+" · "+(100*black/samples)+"% da amostra em preto puro");
            }
            require(b.compress(Bitmap.CompressFormat.PNG,100,file),"Falha no PNG.");
        } finally { b.recycle(); }
    }

    private void backupCheck() throws Exception {
        JSONObject example = J.obj("id", QA_NIGHT, "notes","Registro sintético de QA","quality",4);
        store.put("night",example);
        JSONObject persisted = new Store(target).get("night",QA_NIGHT);
        require(persisted != null && persisted.getInt("quality")==4 && !persisted.has("fatigue"),
            "O registro SQLite perdeu um valor ou substituiu uma resposta ausente.");
        File backup = File.createTempFile("qa-backup-", ".json", target.getCacheDir());
        try {
            try (FileOutputStream file = new FileOutputStream(backup)) {
                file.write(store.exportData().toString().getBytes(StandardCharsets.UTF_8));
            }
            try (FileInputStream file = new FileInputStream(backup)) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096]; int count;
                while ((count = file.read(chunk)) != -1) bytes.write(chunk,0,count);
                JSONObject imported = new JSONObject(bytes.toString("UTF-8"));
                require(imported.getInt("version")==1 && imported.getJSONArray("night").length()==1,"Backup inválido.");
                J.put(example,"quality",1); store.put("night",example);
                store.replaceData(imported);
            }
            require(new Store(target).get("night",QA_NIGHT).getInt("quality")==4,"Restauração não recuperou o valor.");
        } finally { store.remove("night",QA_NIGHT); if (!backup.delete()) backup.deleteOnExit(); }
        passed("Persistência entre instâncias e restauração do backup JSON temporário");
    }

    private Notification ownAlarmNotice() {
        for (StatusBarNotification item : target.getSystemService(NotificationManager.class).getActiveNotifications()) {
            if (Notification.CATEGORY_ALARM.equals(item.getNotification().category)) return item.getNotification();
        }
        return null;
    }

    private boolean playingAlarm() {
        for (AudioPlaybackConfiguration a : target.getSystemService(AudioManager.class).getActivePlaybackConfigurations())
            if (a.getAudioAttributes().getUsage()==AudioAttributes.USAGE_ALARM) return true;
        return false;
    }

    private void noticeAction(String prefix) throws Exception {
        Notification notice = ownAlarmNotice();
        require(notice != null && notice.actions != null, "A notificação não expôs controles.");
        for (Notification.Action action : notice.actions) {
            if (action.title != null && action.title.toString().startsWith(prefix)) {
                action.actionIntent.send();
                return;
            }
        }
        throw new AssertionError("Ação ausente da notificação: " + prefix);
    }

    private void alarmCheck() throws Exception {
        require(Scheduler.exact(target), "Conceda alarmes exatos no emulador de QA.");
        require(target.getSystemService(NotificationManager.class).areNotificationsEnabled(), "Ative as notificações de QA.");
        require(!playingAlarm() && Scheduler.ringing(target)==null, "Há outro alarme tocando no dispositivo.");
        long at = System.currentTimeMillis()+8000, deadline=at+28000;
        store.put("night",J.obj("id",QA_NIGHT,"bed",at-8*3600000L,"latency",15));
        JSONObject alarm = J.obj("id",QA_ALARM,"name","Teste instrumental Nox","enabled",true,
            "at",at,"deadline",deadline,"night",QA_NIGHT,"snooze",1,"maxSnoozes",1,
            "tone","content://br.app.nox.dev.test.arquivo-inexistente/alarme","volume",15,"vibrate",false,"ramp",false);
        Scheduler.put(target,alarm);
        JSONObject scheduled = Scheduler.next(target);
        require(scheduled!=null && scheduled.getLong("at")==at,"O agendamento não apareceu no Android.");
        onUi(() -> activity.moveTaskToBack(true));
        waitFor("primeiro alarme, serviço em foreground e reprodução USAGE_ALARM",22000,
            () -> Scheduler.ringing(target)!=null && ownAlarmNotice()!=null && playingAlarm());
        JSONObject first = store.get("night",QA_NIGHT);
        long actual = first.getLong("firstRing");
        require(actual>=at-1000 && actual-at<=12000, "Disparo fora da tolerância do teste.");
        require(first.getJSONArray("rings").length()==1 && !first.has("wake"),"Registro de disparo incorreto.");
        // The optional full-screen grant is independent. This sends the real notification's content intent.
        ownAlarmNotice().contentIntent.send();
        waitFor("tela do alarme",5000,this::targetInFront);
        capture("alarme-tocando");
        noticeAction("Adiar");
        waitFor("silêncio e soneca",5000,() -> !playingAlarm() && Scheduler.ringing(target)==null);
        JSONObject snoozed = Scheduler.next(target);
        require(snoozed!=null && snoozed.getLong("at")==deadline, "Soneca não respeitou o horário-limite.");
        waitFor("segundo disparo e áudio",41000,
            () -> Scheduler.ringing(target)!=null && ownAlarmNotice()!=null && playingAlarm());
        noticeAction("Despertei");
        waitFor("interrupção do áudio e encerramento",7000,
            () -> !playingAlarm() && Scheduler.ringing(target)==null && ownAlarmNotice()==null);
        JSONObject end = store.get("night",QA_NIGHT);
        require(end.getJSONArray("rings").length()==2 && end.getJSONArray("snoozes").length()==1,
            "Cronologia dos disparos e da soneca incorreta.");
        require(end.has("wake") && !end.has("up"),"O botão Despertei deve deixar a saída da cama pendente.");
        passed("Alarme com app em segundo plano, fonte indisponível, reprodução USAGE_ALARM, soneca no limite, segundo disparo e encerramento; primeiro disparo delta=" + (actual-at) + " ms");
    }
}
