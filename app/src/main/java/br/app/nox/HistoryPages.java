package br.app.nox;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HistoryPages {
    private static final int SAVE=604;
    private static final ExecutorService PDF_WORK=Executors.newSingleThreadExecutor();
    private static final int[] DAYS={7,14,30,90,0,-1};
    private static final String[] PERIOD={"Últimos 7 dias","Últimos 14 dias","Últimos 30 dias","Últimos 90 dias","Histórico completo","Período personalizado"};
    private final MainActivity m;
    private final SharedPreferences state;
    private int days,metric;
    private LocalDate customFrom,customTo;
    private boolean busy;
    private int visible=15;

    public HistoryPages(MainActivity m){
        this.m=m;
        state=m.getSharedPreferences("history-display",0);
        days=state.getInt("days",7);
        metric=state.getInt("metric",0);
        customFrom=parse("from",LocalDate.now().minusDays(6));
        customTo=parse("to",LocalDate.now());
    }
    private LocalDate parse(String key,LocalDate fallback) {
        try{return LocalDate.parse(state.getString(key,""));} catch(RuntimeException e){return fallback;}
    }
    private DiaryStats data() {
        LocalDate today=LocalDate.now();
        return new DiaryStats(m.store,days==0?null:days==-1?customFrom:today.minusDays(days-1),
                days==0?null:days==-1?customTo:today);
    }
    private String selectedPeriod() {
        for(int i=0;i<DAYS.length;i++) if(DAYS[i]==days) return days==-1?customFrom+" a "+customTo:PERIOD[i];
        return PERIOD[0];
    }
    public void show() {
        DiaryStats s=data();
        m.page("Histórico","Seu diário e suas tendências",3);
        m.card("Período",selectedPeriod(),()->pickRange(this::show));
        m.card("Calendário",s.nights.size()+" noites no período",()->calendar(currentMonth(s),s));
        if(s.nights.isEmpty()){
            m.heading("Nenhuma noite neste período");
            m.note("Você pode consultar outras datas pelo filtro de período. O diário também inclui despertares registrados sem horário de deitar.");
        } else {
            m.heading("Duração do sono");
            m.label(s.aggregate(0,-1));
            m.note("Média estimada · "+s.values(0,-1).size()+" de "+s.nights.size()+" noites com duração calculável.");
            if(s.upperCount()>0) m.note(s.upperCount()+" estimativas são limites superiores porque faltou informar o tempo acordado à noite.");
            m.card("Indicadores e gráficos","Duração, regularidade, disposição e meta",this::statistics);
        }
        m.button("Exportar relatório do sono",this::export);
        if(s.independentDays>0) m.card("Sonolência sem noite vinculada",s.independentDays+" avaliações no período",()->standalone(s));
        if(!s.nights.isEmpty()) {
            m.heading("Noites registradas");
            for(int i=s.nights.size()-1;i>=Math.max(0,s.nights.size()-visible);i--) nightCard(s.nights.get(i));
            if(s.nights.size()>visible) m.button("Mostrar mais noites",()->{visible+=15;show();});
        }
    }
    private void nightCard(JSONObject n) {
        String q=DiaryStats.option(n,"quality",DiaryStats.QUALITY,1);
        m.card(DiaryStats.date(n)+"  ·  "+DiaryStats.clock(n.optLong("bed"),n)+" – "+DiaryStats.clock(n.optLong("wake"),n),
                "Sono: "+DiaryStats.durationText(n)+"  ·  Qualidade: "+q,()->details(n.optString("id")));
    }
    public void details(String id) {
        JSONObject n=m.store.get("night",id);
        if(n==null){m.message("Este registro foi excluído.");show();return;}
        DiaryStats all=new DiaryStats(m.store,null,null);
        m.page("Sua noite",DiaryStats.date(n),3);
        m.label(DiaryStats.durationText(n));
        m.note("Duração estimada do sono");
        m.button("Editar horários e avaliação",()->m.sleep.morning(id));
        for(String[] row:SleepReport.details(all,n)) {
            m.heading(row[0]);
            m.note(row[1]);
        }
        m.button("Excluir esta noite",()->new AlertDialog.Builder(m).setTitle("Excluir a noite de "+DiaryStats.date(n)+"?")
            .setMessage("A noite e as avaliações diurnas vinculadas serão apagadas. Esta ação é permanente.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,k)->{m.store.remove("night",id);m.message("Noite excluída.");show();}).show());
        m.button("Voltar ao histórico",this::show);
    }
    private void standalone(DiaryStats s) {
        m.page("Sonolência diurna","Avaliações sem noite vinculada",3);
        for(int i=s.unlinked.size()-1;i>=0;i--){
            JSONObject d=s.unlinked.get(i);
            m.heading(DiaryStats.fmt(d.optLong("at"),d));m.note(SleepReport.dayText(d));
        }
        m.button("Voltar ao histórico",this::show);
    }
    private interface DateAction { void accept(LocalDate date); }
    private void chooseDate(String title,LocalDate initial,LocalDate min,LocalDate max,DateAction done) {
        DatePickerDialog pick=new DatePickerDialog(m,(v,y,mo,d)->done.accept(LocalDate.of(y,mo+1,d)),
            initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth());
        pick.setTitle(title);
        if(min!=null) pick.getDatePicker().setMinDate(min.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        if(max!=null) pick.getDatePicker().setMaxDate(max.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()-1);
        pick.show();
    }
    private void pickRange(Runnable next) {
        new AlertDialog.Builder(m).setTitle("Período do diário").setItems(PERIOD,(d,k)->{
            if(DAYS[k]!=-1) {days=DAYS[k];persistRange();next.run();return;}
            LocalDate today=LocalDate.now();
            chooseDate("Primeira data",customFrom.isAfter(today)?today:customFrom,null,today,from->{
                LocalDate firstEnd=customTo.isBefore(from)?from:customTo.isAfter(today)?today:customTo;
                chooseDate("Última data",firstEnd,from,today,to->{customFrom=from;customTo=to;days=-1;persistRange();next.run();});
            });
        }).show();
    }
    private void persistRange() {
        visible=15;
        state.edit().putInt("days",days).putString("from",customFrom.toString()).putString("to",customTo.toString()).apply();
    }
    private YearMonth currentMonth(DiaryStats s) {
        if(!s.nights.isEmpty()) return YearMonth.from(LocalDate.parse(DiaryStats.date(s.nights.get(s.nights.size()-1))));
        return YearMonth.from(s.to==null?LocalDate.now():s.to);
    }
    private void calendar(YearMonth month,DiaryStats s) {
        String title=month.format(DateTimeFormatter.ofPattern("MMMM 'de' uuuu",DiaryStats.PT));
        m.page("Calendário",title,3);
        m.note("Dias destacados têm pelo menos uma noite no período selecionado.");
        LinearLayout controls=row();
        smallButton(controls,"‹ Mês","Mês anterior",()->calendar(month.minusMonths(1),s));
        smallButton(controls,"Ir para…","Escolher mês e data",()->chooseDate("Ir à data",month.atDay(1),null,null,d->calendar(YearMonth.from(d),s)));
        smallButton(controls,"Mês ›","Próximo mês",()->calendar(month.plusMonths(1),s));
        LinearLayout header=row();
        for(String w:new String[]{"Seg","Ter","Qua","Qui","Sex","Sáb","Dom"}){
            TextView t=dayLabel(w);header.addView(t,new LinearLayout.LayoutParams(0,m.dp(38),1));
        }
        LocalDate first=month.atDay(1);
        int offset=first.getDayOfWeek().getValue()-1;
        int slots=((offset+month.lengthOfMonth()+6)/7)*7;
        LinearLayout week=null;
        for(int i=0;i<slots;i++) {
            if(i%7==0) week=row();
            int dom=i-offset+1;
            boolean valid=dom>=1 && dom<=month.lengthOfMonth();
            TextView cell=dayLabel(valid?String.valueOf(dom):"");
            week.addView(cell,new LinearLayout.LayoutParams(0,m.dp(48),1));
            if(!valid) continue;
            LocalDate date=month.atDay(dom);
            List<JSONObject> list=new ArrayList<>();
            for(JSONObject n:s.nights) if(DiaryStats.date(n).equals(date.toString())) list.add(n);
            if(list.isEmpty()) {cell.setTextColor(m.muted);continue;}
            GradientDrawable marker=new GradientDrawable();
            marker.setColor(m.surface);marker.setStroke(m.dp(1),m.accent);marker.setCornerRadius(m.dp(16));
            cell.setBackground(marker);cell.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            cell.setContentDescription(date+" · "+list.size()+" noites. Abrir registro.");
            cell.setFocusable(true);
            cell.setOnClickListener(v->{
                if(list.size()==1) details(list.get(0).optString("id"));
                else {m.page("Noites do dia",date.toString(),3);for(JSONObject n:list) nightCard(n);m.button("Voltar ao calendário",()->calendar(month,s));}
            });
        }
        m.button("Voltar ao histórico",this::show);
    }
    private LinearLayout row(){LinearLayout r=new LinearLayout(m);r.setOrientation(LinearLayout.HORIZONTAL);m.body.addView(r,-1,-2);return r;}
    private TextView dayLabel(String text){TextView t=new TextView(m);t.setText(text);t.setTextColor(m.ink);t.setGravity(Gravity.CENTER);t.setTextSize(14);return t;}
    private void smallButton(LinearLayout row,String title,String description,Runnable action){
        Button b=new Button(m);b.setAllCaps(false);b.setText(title);b.setTextSize(13);b.setTextColor(m.ink);
        b.setBackgroundTintList(ColorStateList.valueOf(m.surface));b.setContentDescription(description);b.setOnClickListener(v->action.run());
        row.addView(b,new LinearLayout.LayoutParams(0,m.dp(54),1));
    }
    public void statistics() {
        DiaryStats s=data();
        m.page("Estatísticas",selectedPeriod(),3);
        m.card("Período",selectedPeriod(),()->pickRange(this::statistics));
        m.card("Indicador",DiaryStats.TITLES[metric],()->new AlertDialog.Builder(m).setTitle("Indicador do gráfico")
            .setItems(DiaryStats.TITLES,(d,k)->{metric=k;state.edit().putInt("metric",k).apply();statistics();}).show());
        m.heading(DiaryStats.TITLES[metric]+" ("+DiaryStats.UNITS[metric]+")");
        m.label(s.aggregate(metric,-1));
        m.note(s.values(metric,-1).size()+" noites com valor no período · horários em mediana, demais indicadores em média ou proporção.");
        DiaryChart chart=new DiaryChart(m,s,metric,m.ink);
        m.body.addView(chart,new LinearLayout.LayoutParams(-1,m.dp(175)));
        m.heading("Dias úteis e fins de semana");
        m.note("Segunda a sexta · "+s.aggregate(metric,0)+" ("+s.values(metric,0).size()+" noites)\nSábado e domingo · "+s.aggregate(metric,1)+" ("+s.values(metric,1).size()+" noites)");
        m.heading("Regularidade do despertar");
        m.note(DiaryStats.span(DiaryMath.clockSpread(s.values(3,-1)))+" · dispersão dos horários em minutos, considerando a passagem pela meia-noite.");
        m.heading("Completude dos registros");
        m.note(s.completeness()+" · despertar, levantar, qualidade, cansaço, cefaleia, despertares, tempo acordado e latência relatada.");
        if(metric==0) m.note(s.upperCount()+" limites superiores incluídos. Quando falta o tempo acordado à noite, a duração pode estar superestimada.");
        if(metric==8) m.note("¹ A categoria «quatro ou mais» conta como quatro na média.");
        if(metric==12) m.note("Apenas noites com resultado determinável entram no percentual. Uma estimativa máxima abaixo da meta permite classificar a noite como insuficiente.");
        if(metric==13) m.note("Percentual do tempo na cama estimado como sono. Exige latência em minutos, tempo acordado à noite e horários de deitar, acordar e levantar.");
        m.button("Voltar ao histórico",this::show);
    }
    private void export(){
        DiaryStats s=data();
        m.page("Exportar relatório do sono","PDF com texto selecionável",3);
        m.card("Período do relatório",selectedPeriod(),()->pickRange(this::export));
        m.note(s.nights.size()+" noites · Completude: "+s.completeness()+". O PDF reúne indicadores, gráfico, tabela por noite, informações ausentes e um prompt para análise.");
        m.button("Salvar PDF",()->{
            if(busy) {m.message("O PDF está sendo gerado.");return;}
            state.edit().putString("exportFrom",s.from==null?"":s.from.toString()).putString("exportTo",s.to==null?"":s.to.toString()).apply();
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf")
                .putExtra(Intent.EXTRA_TITLE,"Nox-Sono-"+LocalDate.now()+".pdf");
            try {m.startActivityForResult(i,SAVE);} catch(RuntimeException e){m.message("O seletor de arquivos não está disponível.");}
        });
        m.button("Compartilhar PDF",()->generate(s,null));
        m.button("Voltar ao histórico",this::show);
    }
    public void result(int requestCode,int resultCode,Intent data) {
        if(requestCode!=SAVE||resultCode!=Activity.RESULT_OK||data==null||data.getData()==null) return;
        generate(new DiaryStats(m.store,parse("exportFrom",null),parse("exportTo",null)),data.getData());
    }
    private void generate(DiaryStats stats,Uri destination) {
        if(busy){m.message("O PDF está sendo gerado.");return;}
        busy=true;m.message("Gerando relatório…");
        JSONObject cfg=m.config();
        PDF_WORK.execute(()->{
            File temp=null;
            try {
                File dir=new File(m.getCacheDir(),"reports");
                if(!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Falha ao criar pasta temporária");
                File[] old=dir.listFiles();
                if(old!=null) for(File f:old) if(f.getName().matches("nox-[0-9a-f-]+\\.pdf") && f.lastModified()<System.currentTimeMillis()-7L*86400000) f.delete();
                temp=new File(dir,"nox-"+UUID.randomUUID()+".pdf");
                try(OutputStream out=new FileOutputStream(temp)){SleepReport.write(stats,cfg,out);}
                if(destination!=null) {
                    try(FileInputStream in=new FileInputStream(temp);OutputStream out=m.getContentResolver().openOutputStream(destination,"wt")){
                        if(out==null) throw new IOException("Destino indisponível");
                        byte[] buf=new byte[32768];int n;
                        while((n=in.read(buf))!=-1) out.write(buf,0,n);
                    }
                    temp.delete();
                    m.runOnUiThread(()->done("Relatório salvo."));
                } else {
                    Uri uri=new Uri.Builder().scheme("content").authority(m.getPackageName()+".reports").appendPath(temp.getName()).build();
                    m.runOnUiThread(()->{
                        busy=false;
                        if(m.isFinishing()||m.isDestroyed())return;
                        Intent send=new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri);
                        send.setClipData(ClipData.newRawUri("Relatório do sono",uri));
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try{m.startActivity(Intent.createChooser(send,"Compartilhar relatório do sono"));}catch(RuntimeException e){m.message("Nenhum aplicativo disponível para compartilhar o PDF.");}
                    });
                }
            } catch(Exception e){
                if(temp!=null)temp.delete();
                m.runOnUiThread(()->done("Não foi possível gravar o PDF. Confira o espaço livre e tente outro destino."));
            }
        });
    }
    private void done(String text){busy=false;if(!m.isFinishing()&&!m.isDestroyed())m.message(text);}
}
