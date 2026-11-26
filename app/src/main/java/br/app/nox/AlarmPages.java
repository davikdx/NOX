package br.app.nox;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Alarm management and device settings. A detached JSON draft keeps edits atomic. */
public final class AlarmPages {
    private static final int FILE_PICK = 401, TONE_PICK = 402, SYSTEM_SETTINGS = 403;
    private static final Locale BR = new Locale("pt", "BR");
    private static final String[] DAYS = {"Segunda-feira", "Terça-feira", "Quarta-feira",
            "Quinta-feira", "Sexta-feira", "Sábado", "Domingo"};
    private static final String[] SHORT_DAYS = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
    private static final String[] SOUND_KEYS = {"tone", "toneName", "volume", "ramp",
            "vibrate", "snooze", "maxSnoozes"};

    private final MainActivity m;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable endSample = this::stopPreview;
    private MediaPlayer sample;
    private Button sampleButton;
    private JSONObject draft;
    private boolean editingDefaults;

    public AlarmPages(MainActivity activity) { m = activity; }

    public void show() {
        discard();
        m.page("Alarmes", "Seus horários de despertar", 1);
        JSONObject next = Scheduler.next(m);
        if (next != null) {
            long at = next.optLong("at");
            m.card("Próximo despertar  ·  " + clockAt(at), dateAt(at) + "  ·  " + remaining(at),
                    this::diagnostics);
        }
        m.button("Criar alarme", () -> edit(null));
        List<JSONObject> alarms = new ArrayList<>(m.store.all("alarm"));
        alarms.sort((a, b) -> {
            int enabled = Boolean.compare(b.optBoolean("enabled"), a.optBoolean("enabled"));
            return enabled != 0 ? enabled : Integer.compare(a.optInt("minute"), b.optInt("minute"));
        });
        alarms.removeIf(a -> !a.optBoolean("enabled", true) && a.optLong("deadline") > 0);
        if (alarms.isEmpty()) m.note("Crie um alarme para um horário único ou escolha os dias de repetição.");
        boolean exact = Scheduler.exact(m);
        for (JSONObject alarm : alarms) {
            String id = alarm.optString("id");
            String title = name(alarm);
            boolean enabled = alarm.optBoolean("enabled", true);
            boolean smart = alarm.optLong("deadline") > 0 || !alarm.optString("night").isEmpty();
            int minute = alarm.optInt("minute", 0);
            boolean test = alarm.optBoolean("_test");
            String time = (smart || test) && alarm.optLong("at") > 0 ? clockAt(alarm.optLong("at")) : clock(minute);
            String state = enabled ? "Ativado" : "Desativado";
            if (enabled && !exact) state = "Pendente de permissão";
            String subtitle = title + "  ·  " + (smart ? "Sessão de sono" : daysText(alarm.optInt("days")))
                    + "\n" + state;
            m.card(time, subtitle, () -> {
                if (test) confirmRemove(alarm);
                else if (smart) smartInfo(alarm);
                else actions(id);
            });
        }
        m.button("Confiabilidade do despertador", this::diagnostics);
    }

    private void smartInfo(JSONObject a) {
        String message = "Horário-limite " + clockAt(a.optLong("deadline"))
                + ". O planejamento desta noite fica na área Sono.";
        new AlertDialog.Builder(m).setTitle(name(a)).setMessage(message)
                .setPositiveButton("Fechar", null)
                .setNeutralButton("Cancelar este alarme", (d, w) -> confirmRemove(a)).show();
    }

    private void actions(String id) {
        JSONObject a = m.store.get("alarm", id);
        if (a == null) { show(); return; }
        boolean enabled = a.optBoolean("enabled", true);
        ArrayList<String> choices = new ArrayList<>();
        choices.add("Editar");
        choices.add(enabled ? "Desativar" : "Ativar");
        choices.add("Duplicar");
        if (enabled && a.optInt("days") != 0) choices.add("Ignorar a próxima ocorrência");
        choices.add("Excluir");
        new AlertDialog.Builder(m).setTitle(clock(a.optInt("minute")) + "  ·  " + name(a))
                .setItems(choices.toArray(new String[0]), (dialog, index) -> {
                    String choice = choices.get(index);
                    if (choice.equals("Editar")) { edit(id); return; }
                    if (choice.equals("Excluir")) { confirmRemove(a); return; }
                    if (choice.equals("Duplicar")) {
                        draft = J.copy(a);
                        J.put(draft, "id", UUID.randomUUID().toString());
                        J.put(draft, "name", name(a) + " (cópia)");
                        editingDefaults = false;
                        form();
                        return;
                    }
                    try {
                        if (choice.startsWith("Ignorar")) Scheduler.skip(m, id);
                        else {
                            if (!enabled && !Scheduler.exact(m)) { explainExact(); return; }
                            J.put(a, "enabled", !enabled);
                            Scheduler.put(m, a);
                        }
                        show();
                    } catch (RuntimeException e) { problem(e); }
                }).show();
    }

    private void confirmRemove(JSONObject a) {
        new AlertDialog.Builder(m).setTitle("Excluir este alarme?")
                .setMessage((a.optLong("at") > 0 ? clockAt(a.optLong("at")) : clock(a.optInt("minute"))) + "  ·  " + name(a))
                .setNegativeButton("Voltar", null)
                .setPositiveButton("Excluir", (dialog, which) -> {
                    try { Scheduler.remove(m, a.optString("id")); show(); }
                    catch (RuntimeException e) { problem(e); }
                }).show();
    }

    public void edit(String id) {
        discard();
        if (id != null) {
            JSONObject saved = m.store.get("alarm", id);
            if (saved == null) { show(); return; }
            draft = J.copy(saved);
        } else {
            JSONObject config = m.config();
            draft = J.obj("id", UUID.randomUUID().toString(), "name", "Despertador",
                    "minute", config.optInt("wakeMinute", 420), "days", 0, "enabled", true);
            copySound(config, draft);
        }
        form();
    }

    public void defaults() {
        discard();
        editingDefaults = true;
        draft = new JSONObject();
        copySound(m.config(), draft);
        form();
    }

    private static void copySound(JSONObject from, JSONObject to) {
        for (String key : SOUND_KEYS) if (from.has(key)) J.put(to, key, from.opt(key));
    }

    private void form() {
        if (draft == null) { show(); return; }
        stopPreview();
        m.page(editingDefaults ? "Som e soneca" : "Configurar alarme",
                editingDefaults ? "Padrão para novos alarmes e sessões" : "Horário, repetição e som", 1);
        if (!editingDefaults) {
            int minute = draft.optInt("minute", 420);
            m.card(clock(minute), "Escolher horário", () ->
                    new TimePickerDialog(m, (v, h, min) -> {
                        J.put(draft, "minute", h * 60 + min);
                        form();
                    }, minute / 60, minute % 60, true).show());
            textField("Nome do alarme", "name");
            m.card("Repetir", daysText(draft.optInt("days")), this::chooseDays);
            toggle("Alarme ativado", "enabled", true);
        }
        m.heading("Som ao despertar");
        m.card("Som do alarme", toneName(draft), this::chooseTone);
        slider();
        toggle("Aumentar volume aos poucos", "ramp", true);
        toggle("Vibrar", "vibrate", true);
        sampleButton = m.button("Ouvir prévia", () -> {
            if (sample != null) { stopPreview(); return; }
            playPreview();
        });
        m.heading("Soneca");
        numberSetting("Duração", "snooze", 5, 1, 60, true);
        numberSetting("Limite de sonecas", "maxSnoozes", 0, 0, 30, false);
        m.button(editingDefaults ? "Salvar preferências" : "Salvar alarme", this::save);
        m.button("Cancelar", () -> {
            boolean wasDefaults = editingDefaults;
            discard();
            if (wasDefaults) m.settings(); else show();
        });
    }

    private void textField(String hint, String key) {
        TextView caption = new TextView(m);
        caption.setText(hint);
        caption.setTextColor(m.muted);
        m.body.addView(caption);
        EditText field = new EditText(m);
        field.setSingleLine(true);
        field.setSelectAllOnFocus(true);
        field.setTextColor(m.ink);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setText(draft.optString(key));
        int fieldId = android.view.View.generateViewId();
        field.setId(fieldId);
        caption.setLabelFor(fieldId);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                if (draft != null) J.put(draft, key, s.toString());
            }
            public void afterTextChanged(Editable e) {}
        });
        m.body.addView(field);
    }

    private void toggle(String label, String key, boolean fallback) {
        Switch control = new Switch(m);
        control.setText(label);
        control.setTextColor(m.ink);
        control.setTextSize(16);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setMinHeight(m.dp(52));
        control.setChecked(draft.optBoolean(key, fallback));
        control.setOnCheckedChangeListener((v, checked) -> J.put(draft, key, checked));
        m.body.addView(control);
    }

    private void slider() {
        int value = Math.max(1, Math.min(100, draft.optInt("volume", 85)));
        TextView label = new TextView(m);
        label.setTextColor(m.ink);
        label.setText("Volume do Nox  ·  " + value + "%");
        m.body.addView(label);
        SeekBar slider = new SeekBar(m);
        slider.setMax(100);
        slider.setMin(1);
        slider.setProgress(value);
        slider.setMinimumHeight(m.dp(48));
        slider.setContentDescription("Volume do Nox, em porcentagem");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int value, boolean user) {
                J.put(draft, "volume", value);
                label.setText("Volume do Nox  ·  " + value + "%");
                if (sample != null) try { sample.setVolume(value / 100f, value / 100f); }
                catch (IllegalStateException ignored) {}
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        m.body.addView(slider);
    }

    private void numberSetting(String title, String key, int initial, int low, int high, boolean minutes) {
        int value = draft.optInt(key, initial);
        String display = minutes ? value + " min" : (value == 0 ? "Sem limite" : String.valueOf(value));
        m.card(title, display, () -> {
            EditText field = new EditText(m);
            field.setSingleLine(true);
            field.setInputType(InputType.TYPE_CLASS_NUMBER);
            field.setText(String.valueOf(value));
            field.selectAll();
            LinearLayout layout = new LinearLayout(m);
            layout.setPadding(m.dp(24), 0, m.dp(24), 0);
            layout.addView(field, new LinearLayout.LayoutParams(-1, -2));
            String instruction = minutes ? "Escolha de 1 a 60 minutos." : "Use 0 para sonecas ilimitadas ou escolha de 1 a 30.";
            AlertDialog dialog = new AlertDialog.Builder(m).setTitle(title).setMessage(instruction)
                    .setView(layout).setNegativeButton("Cancelar", null).setPositiveButton("Aplicar", null).create();
            dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
                try {
                    int selected = Integer.parseInt(field.getText().toString());
                    if (selected < low || selected > high) throw new NumberFormatException();
                    J.put(draft, key, selected);
                    dialog.dismiss();
                    form();
                } catch (NumberFormatException e) { field.setError("Escolha um valor de " + low + " a " + high); }
            }));
            dialog.show();
        });
    }

    private void chooseDays() {
        int mask = draft.optInt("days");
        boolean[] checked = new boolean[7];
        for (int i = 0; i < 7; i++) checked[i] = (mask & (1 << i)) != 0;
        new AlertDialog.Builder(m).setTitle("Dias de repetição")
                .setMultiChoiceItems(DAYS, checked, (d, i, b) -> checked[i] = b)
                .setNeutralButton("Uma vez", (d, w) -> { J.put(draft, "days", 0); form(); })
                .setNegativeButton("Cancelar", null).setPositiveButton("Aplicar", (d, w) -> {
                    int chosen = 0;
                    for (int i = 0; i < 7; i++) if (checked[i]) chosen |= 1 << i;
                    J.put(draft, "days", chosen);
                    form();
                }).show();
    }

    private void chooseTone() {
        String[] names = {"Nox · Noite serena", "Nox · Aurora", "Alarme padrão do aparelho",
                "Escolher toque do aparelho", "Escolher música no celular"};
        new AlertDialog.Builder(m).setTitle("Som do alarme").setItems(names, (d, index) -> {
            if (index < 3) {
                String[] ids = {"nox", "dawn", "system"};
                setTone(ids[index], names[index]);
                form();
                return;
            }
            if (index == 3) {
                Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                launch(i, TONE_PICK);
            } else {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*")
                        .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                launch(i, FILE_PICK);
            }
        }).show();
    }

    private void setTone(String source, String label) {
        J.put(draft, "tone", source);
        J.put(draft, "toneName", label);
    }

    public void result(int requestCode, int resultCode, Intent data) {
        if (requestCode == SYSTEM_SETTINGS) { diagnostics(); return; }
        if (requestCode != FILE_PICK && requestCode != TONE_PICK) return;
        if (draft == null || resultCode != Activity.RESULT_OK || data == null) return;
        Uri uri = requestCode == FILE_PICK ? data.getData()
                : (Uri) data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        if (uri == null) return;
        try {
            if (requestCode == FILE_PICK) {
                m.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                setTone(uri.toString(), displayName(uri));
            } else if (RingtoneManager.isDefault(uri)) {
                setTone("system", "Alarme padrão do aparelho");
            } else {
                Ringtone ringtone = RingtoneManager.getRingtone(m, uri);
                setTone(uri.toString(), ringtone != null ? ringtone.getTitle(m) : "Toque do aparelho");
            }
        } catch (RuntimeException e) { m.message("O Android não manteve o acesso. Escolha outro arquivo local."); }
        form();
    }

    private String displayName(Uri uri) {
        try (Cursor c = m.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (RuntimeException ignored) {}
        return "Música do celular";
    }

    private static String toneName(JSONObject object) {
        String tone = object.optString("tone", "nox");
        switch (tone) {
            case "nox": return "Nox · Noite serena";
            case "dawn": return "Nox · Aurora";
            case "system": return "Alarme padrão do aparelho";
            default: return object.optString("toneName", "Música selecionada");
        }
    }

    private void playPreview() {
        stopPreview();
        if (Scheduler.ringing(m) != null) {
            m.message("Há um alarme tocando. Encerre-o antes de ouvir uma prévia.");
            return;
        }
        preparePreview(draft.optString("tone", "nox"), true);
    }

    private void preparePreview(String source, boolean fallback) {
        MediaPlayer player = new MediaPlayer();
        sample = player;
        try {
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            if ("nox".equals(source) || "dawn".equals(source)) {
                int res = "dawn".equals(source) ? R.raw.dawn : R.raw.nox;
                try (AssetFileDescriptor afd = m.getResources().openRawResourceFd(res)) {
                    player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                }
            } else {
                Uri uri = "system".equals(source) ? RingtoneManager.getActualDefaultRingtoneUri(m, RingtoneManager.TYPE_ALARM)
                        : Uri.parse(source);
                if (uri == null) throw new IOException("Som indisponível");
                player.setDataSource(m, uri);
            }
            float level = Math.max(1, Math.min(100, draft.optInt("volume", 85))) / 100f;
            player.setVolume(level, level);
            player.setOnPreparedListener(mp -> {
                if (sample == mp) {
                    try {
                        mp.start();
                        if (sampleButton != null) sampleButton.setText("Parar prévia");
                    }
                    catch (RuntimeException e) { previewFailure(source, fallback); }
                }
            });
            player.setOnCompletionListener(mp -> { if (sample == mp) stopPreview(); });
            player.setOnErrorListener((mp, what, extra) -> {
                if (sample == mp) previewFailure(source, fallback);
                return true;
            });
            player.prepareAsync();
            handler.postDelayed(endSample, 9000);
        } catch (IOException | RuntimeException e) { previewFailure(source, fallback); }
    }

    private void previewFailure(String source, boolean fallback) {
        stopPreview();
        if (fallback && !"nox".equals(source)) {
            m.message("Som indisponível. Tocando a prévia do som de segurança.");
            preparePreview("nox", false);
        } else { m.message("Não foi possível reproduzir a prévia neste aparelho."); }
    }

    public void stopPreview() {
        handler.removeCallbacks(endSample);
        if (sampleButton != null) sampleButton.setText("Ouvir prévia");
        MediaPlayer player = sample;
        sample = null;
        if (player != null) {
            player.setOnPreparedListener(null);
            player.setOnErrorListener(null);
            player.setOnCompletionListener(null);
            player.release();
        }
    }

    private void save() {
        if (draft == null) return;
        try {
            if (editingDefaults) {
                JSONObject config = m.config();
                copySound(draft, config);
                m.saveConfig(config);
                discard();
                m.settings();
                m.message("Preferências salvas");
            } else {
                if (draft.optBoolean("enabled", true) && !Scheduler.exact(m)) { explainExact(); return; }
                J.put(draft, "name", name(draft));
                Scheduler.put(m, draft);
                boolean active = draft.optBoolean("enabled", true);
                show();
                m.message(active ? "Alarme agendado" : "Alarme salvo e desativado");
            }
        } catch (RuntimeException e) { problem(e); }
    }

    private void discard() { stopPreview(); sampleButton = null; draft = null; editingDefaults = false; }

    /** Preserve the picker target if Android recreates the activity while browsing music. */
    public void saveState(Bundle out) {
        if (draft != null) {
            out.putString("nox.alarm.draft", draft.toString());
            out.putBoolean("nox.alarm.defaults", editingDefaults);
        }
    }

    public boolean restoreState(Bundle in) {
        if (in == null || !in.containsKey("nox.alarm.draft")) return false;
        try {
            draft = new JSONObject(in.getString("nox.alarm.draft", "{}"));
            editingDefaults = in.getBoolean("nox.alarm.defaults");
            form();
            return true;
        } catch (JSONException e) { discard(); return false; }
    }

    private void explainExact() {
        new AlertDialog.Builder(m).setTitle("Autorize o horário exato")
                .setMessage("Permita alarmes e lembretes nas configurações do Nox. Depois, volte ao aplicativo para concluir o agendamento.")
                .setNegativeButton("Voltar", null)
                .setPositiveButton("Abrir configurações", (d, w) -> openExact(false)).show();
    }

    private void openExact(boolean refresh) {
        if (Build.VERSION.SDK_INT >= 31) {
            open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri()), refresh);
        } else { open(appDetails(), refresh); }
    }

    private void problem(RuntimeException e) {
        String detail = e.getMessage();
        m.message(detail == null || detail.isEmpty() ? "O Android não conseguiu alterar o agendamento." : detail);
    }

    private Uri packageUri() { return Uri.parse("package:" + m.getPackageName()); }
    private Intent appDetails() { return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()); }
    private void launch(Intent intent, int request) {
        try { m.startActivityForResult(intent, request); }
        catch (RuntimeException e) { m.message("O aparelho não oferece essa tela."); }
    }
    private void open(Intent i, boolean refresh) {
        try { if (refresh) m.startActivityForResult(i, SYSTEM_SETTINGS); else m.startActivity(i); }
        catch (RuntimeException e) {
            if (Settings.ACTION_APPLICATION_DETAILS_SETTINGS.equals(i.getAction())) {
                m.message("Abra as configurações do Nox pelo ícone do aplicativo.");
            } else { open(appDetails(), refresh); }
        }
    }

    public void diagnostics() {
        discard();
        m.page("Confiabilidade do despertador", "Condições atuais deste aparelho", 1);
        NotificationManager notifications = m.getSystemService(NotificationManager.class);
        PowerManager power = m.getSystemService(PowerManager.class);
        AudioManager audio = m.getSystemService(AudioManager.class);
        ActivityManager activities = m.getSystemService(ActivityManager.class);
        boolean exact = Scheduler.exact(m);
        m.card("Alarmes exatos", exact ? "Permitidos" : "Pendente · toque para autorizar",
                () -> openExact(true));
        boolean notified = notifications.areNotificationsEnabled();
        m.card("Notificações", notified ? "Permitidas" : "Desativadas · toque para configurar", () -> {
            if (Build.VERSION.SDK_INT >= 33
                    && m.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && m.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                m.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 405);
            } else {
                open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, m.getPackageName()), true);
            }
        });
        RingService.channel(m);
        NotificationChannel channel = notifications.getNotificationChannel(RingService.CHANNEL);
        String channelText = channel == null ? "Indisponível neste aparelho"
                : channel.getImportance() >= NotificationManager.IMPORTANCE_HIGH ? "Prioridade alta"
                : channel.getImportance() == NotificationManager.IMPORTANCE_NONE ? "Bloqueado · toque para habilitar"
                : "Prioridade reduzida · revise os alertas na tela";
        m.card("Canal do despertador", channelText, () -> {
            Intent i = new Intent(channel == null ? Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    : Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, m.getPackageName());
            if (channel != null) i.putExtra(Settings.EXTRA_CHANNEL_ID, RingService.CHANNEL);
            open(i, true);
        });
        boolean full = Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent();
        m.card("Alarme em tela cheia", full ? "Acesso disponível" : "Acesso desativado",
                () -> {
                    if (Build.VERSION.SDK_INT >= 34) open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri()), true);
                    else m.message("Nesta versão do Android, a apresentação depende do canal e do bloqueio de tela.");
                });
        int level = audio.getStreamVolume(AudioManager.STREAM_ALARM);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        m.card("Volume de alarme do aparelho", level + " de " + max + (level == 0 ? " · silencioso" : ""),
                () -> open(new Intent(Settings.ACTION_SOUND_SETTINGS), true));
        boolean optimized = !power.isIgnoringBatteryOptimizations(m.getPackageName());
        String battery = activities.isBackgroundRestricted() ? "Atividade em segundo plano restrita"
                : optimized ? "Otimização padrão do Android" : "Isento da otimização do Android";
        m.card("Bateria e segundo plano", battery + (power.isPowerSaveMode() ? " · economia ligada" : ""),
                () -> open(appDetails(), true));
        if ("samsung".equalsIgnoreCase(Build.MANUFACTURER)) {
            m.card("Samsung One UI", "Conferir suspensão profunda e apps nunca suspensos", () ->
                    new AlertDialog.Builder(m).setTitle("Suspensão no Galaxy")
                            .setMessage("Em Configurações, abra Assistência do aparelho → Bateria → Limites de uso em segundo plano. Remova o Nox de suspensão profunda e adicione-o aos apps nunca suspensos. Os nomes variam com a One UI. O Android não expõe essa lista ao Nox.")
                            .setNegativeButton("Fechar", null).setPositiveButton("Abrir configurações",
                                    (d, w) -> open(new Intent(Settings.ACTION_SETTINGS), true)).show());
        }
        JSONObject next = Scheduler.next(m);
        m.heading("Agenda e música");
        AlarmManager system = m.getSystemService(AlarmManager.class);
        AlarmManager.AlarmClockInfo sysNext = system.getNextAlarmClock();
        String local = next == null ? "Nenhum disparo do Nox confirmado localmente."
                : name(next) + "  ·  " + dateAt(next.optLong("at")) + " às " + clockAt(next.optLong("at"));
        m.note(local);
        boolean ours = sysNext != null && sysNext.getShowIntent() != null
                && m.getPackageName().equals(sysNext.getShowIntent().getCreatorPackage());
        if (sysNext == null) m.note("O Android não informa um próximo despertador.");
        else if (ours) m.note("Próximo no Android: Nox, " + dateAt(sysNext.getTriggerTime()) + " às " + clockAt(sysNext.getTriggerTime()) + ".");
        else m.note("O próximo despertador informado pelo Android está marcado para " + clockAt(sysNext.getTriggerTime())
                + ". O sistema expõe apenas a primeira ocorrência entre os aplicativos.");
        JSONObject selected = next == null ? m.config() : next;
        String soundStatus = soundAccessible(selected) ? "Disponível" : "Indisponível · será usado Noite serena";
        if (next == null) m.card("Som padrão", toneName(selected) + "  ·  " + soundStatus, this::defaults);
        else if (next.optLong("deadline") > 0 || next.optBoolean("_test"))
            m.note("Som do próximo alarme: " + toneName(selected) + "  ·  " + soundStatus);
        else m.card("Som do próximo alarme", toneName(selected) + "  ·  " + soundStatus, () -> edit(next.optString("id")));
        m.button("Testar despertador", () -> {
            if (!Scheduler.exact(m)) { explainExact(); return; }
            new AlertDialog.Builder(m).setTitle("Testar em 30 segundos?")
                    .setMessage("Após agendar, bloqueie a tela. Observe o som, a apresentação e os botões do alarme.")
                    .setNegativeButton("Cancelar", null).setPositiveButton("Agendar teste", (d, w) -> {
                        try {
                            Scheduler.test(m);
                            diagnostics();
                            m.message("Teste agendado para daqui a 30 segundos");
                        } catch (RuntimeException e) { problem(e); }
                    }).show();
        });
        m.note("Aparelho desligado ou sem bateria impede o disparo. Após forçar a parada do Nox, abra-o novamente. Depois de reiniciar o celular, desbloqueie-o uma vez. Confira também se o modo Não perturbe permite alarmes.");
        m.button("Atualizar verificações", this::diagnostics);
    }

    private boolean soundAccessible(JSONObject a) {
        String tone = a.optString("tone", "nox");
        if ("nox".equals(tone) || "dawn".equals(tone)) return true;
        Uri uri = "system".equals(tone) ? RingtoneManager.getActualDefaultRingtoneUri(m, RingtoneManager.TYPE_ALARM) : Uri.parse(tone);
        if (uri == null) return false;
        try (AssetFileDescriptor afd = m.getContentResolver().openAssetFileDescriptor(uri, "r")) { return afd != null; }
        catch (IOException | RuntimeException e) { return false; }
    }

    static String daysText(int mask) {
        if (mask == 0) return "Uma vez";
        if (mask == 127) return "Todos os dias";
        if (mask == 31) return "Segunda a sexta";
        if (mask == 96) return "Fim de semana";
        ArrayList<String> days = new ArrayList<>();
        for (int day = 0; day < 7; day++) if ((mask & (1 << day)) != 0) days.add(SHORT_DAYS[day]);
        return String.join(" · ", days);
    }

    private static String name(JSONObject a) {
        String n = a.optString("name", "").trim();
        return n.isEmpty() ? "Despertador" : n;
    }
    private static String clock(int minute) { return String.format(BR, "%02d:%02d", minute / 60, minute % 60); }
    private static String clockAt(long t) { return new SimpleDateFormat("HH:mm", BR).format(new Date(t)); }
    private static String dateAt(long t) { return new SimpleDateFormat("EEE, dd/MM", BR).format(new Date(t)); }
    private static String remaining(long at) {
        long minutes = Math.max(0, (at - System.currentTimeMillis() + 59999) / 60000);
        if (minutes < 60) return "Em " + minutes + " min";
        return "Em " + (minutes / 60) + " h " + (minutes % 60) + " min";
    }
}
