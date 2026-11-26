package br.app.nox;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Native text and vector export in PDF points, with repeatable table headers at page breaks. */
final class SleepReport {
    private static final int INK=0xff151932, MUTED=0xff4e566b, ACCENT=0xff53449b, SOFT=0xffe6e4f3;
    private final PdfDocument document;
    private PdfDocument.Page page;
    private Canvas canvas;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int sequence;
    private float y;
    private static final float LEFT=40,RIGHT=555,LAST=781;
    private static final String AI_PROMPT="Analise a duração e a regularidade do meu sono. Identifique padrões e possíveis associações entre duração, horários, cansaço, cefaleia e sonolência diurna. Aponte fatores registrados que possam acompanhar sono insuficiente. Quantifique a quantidade de observações em cada comparação e considere os dados ausentes, os limites superiores e as diferenças entre dias úteis e fins de semana. Diferencie associação de causalidade. Sugira ajustes de rotina fundamentados e indique situações que possam justificar avaliação médica, sem formular um diagnóstico definitivo.";

    private SleepReport(PdfDocument document) { this.document=document; }
    static void write(DiaryStats data,JSONObject cfg,OutputStream out) throws IOException {
        PdfDocument pdf=new PdfDocument();
        try {
            SleepReport r=new SleepReport(pdf);
            try {r.render(data,cfg);} finally {if(r.page!=null) r.finish();}
            pdf.writeTo(out);
        } finally {pdf.close();}
    }
    private void style(float size,int color,boolean bold) {
        paint.setColor(color);
        paint.setTypeface(bold?Typeface.create("sans-serif-medium",Typeface.NORMAL):Typeface.create("sans-serif",Typeface.NORMAL));
        paint.setTextSize(size);
    }
    private void newPage() {
        if(page!=null) finish();
        page=document.startPage(new PdfDocument.PageInfo.Builder(595,842,++sequence).create());
        canvas=page.getCanvas();
        style(10,ACCENT,true);canvas.drawText("Nox   /   Diário do sono",LEFT,31,paint);
        paint.setColor(SOFT);canvas.drawLine(LEFT,40,RIGHT,40,paint);
        y=59;
    }
    private void finish() {
        paint.setColor(SOFT);canvas.drawLine(LEFT,799,RIGHT,799,paint);
        style(9,MUTED,false);
        canvas.drawText("Relatório pessoal do sono",LEFT,817,paint);
        canvas.drawText("Página "+sequence,RIGHT-52,817,paint);
        document.finishPage(page); page=null;
    }
    private boolean ensure(float space) {
        if(page==null||y+space>LAST) {newPage();return true;}
        return false;
    }
    private void title(String value) {
        ensure(60); y+=10; style(22,INK,true); canvas.drawText(value,LEFT,y,paint);y+=27;
    }
    private void sub(String value) {
        ensure(47);y+=8;style(14,ACCENT,true);canvas.drawText(value,LEFT,y,paint);y+=20;
    }
    private List<String> wrap(String text,float width,float size,boolean bold) {
        style(size,INK,bold);
        List<String> lines=new ArrayList<>();
        for(String paragraph:text.replace('\r',' ').split("\n",-1)) {
            String rem=paragraph.trim();
            if(rem.isEmpty()) {lines.add(" ");continue;}
            while(!rem.isEmpty()) {
                int n=paint.breakText(rem,true,width,null);
                if(n<=0) n=Character.charCount(rem.codePointAt(0));
                if(n<rem.length()) {
                    int space=rem.lastIndexOf(' ',n);
                    if(space>0) n=space;
                    else if(n>1 && Character.isHighSurrogate(rem.charAt(n-1))) n--;
                }
                lines.add(rem.substring(0,n).trim());
                rem=rem.substring(n).trim();
            }
        }
        return lines;
    }
    private void paragraph(String text) {
        for(String line:wrap(text,RIGHT-LEFT,10,false)){
            ensure(14);style(10,INK,false);canvas.drawText(line,LEFT,y,paint);y+=14;
        }
        y+=4;
    }
    private void field(String key,String text) {
        List<String> lines=wrap(text,RIGHT-LEFT-184,10,false);
        List<String> names=wrap(key,169,10,true);
        int rows=Math.max(names.size(),lines.size());
        if(rows<12) ensure(rows*13+7);
        for(int i=0;i<rows;i++){
            ensure(14);
            if(i<names.size()) {style(10,MUTED,true);canvas.drawText(names.get(i),LEFT,y,paint);}
            if(i<lines.size()) {style(10,INK,false);canvas.drawText(lines.get(i),LEFT+184,y,paint);}
            y+=13;
        }
        paint.setColor(SOFT);canvas.drawLine(LEFT,y-8,RIGHT,y-8,paint);y+=5;
    }
    private void tableRow(String[] cells,float[] starts,boolean header) {
        ensure(17);
        if(header){paint.setColor(0xffeae7f8);canvas.drawRect(LEFT-3,y-11,RIGHT,y+5,paint);}
        style(9,header?ACCENT:INK,header);
        for(int i=0;i<cells.length;i++) {
            canvas.drawText(cells[i],starts[i],y,paint);
        }
        y+=17;
    }
    private void graph(DiaryStats data,int type,String label) {
        ensure(140);
        sub(label);
        DiaryChart.draw(canvas,LEFT,y-5,RIGHT-LEFT,105,data,type,MUTED,ACCENT);
        y+=110;
    }
    private void render(DiaryStats data,JSONObject cfg) {
        newPage();title("Relatório do sono");
        paragraph("Período: "+data.period()+". A data de referência é a de deitar. Em registros parciais de alarme, usa-se a data de despertar, do primeiro disparo ou da saída da cama, nessa ordem.");
        paragraph("Gerado em "+ZonedDateTime.now().format(DiaryStats.MOMENT)+" · Fuso atual: "+data.zone.getId()+".");
        sub("Resumo do período");
        field("Noites registradas",String.valueOf(data.nights.size()));
        field("Completude dos oito campos",data.completeness());
        field("Duração média estimada",data.aggregate(0,-1)+" · "+data.values(0,-1).size()+" noites com valor, incluindo "+data.upperCount()+" limites superiores sem tempo acordado informado.");
        field("Duração mediana estimada",DiaryStats.span(DiaryMath.median(data.values(0,-1))));
        field("Variação do despertar",DiaryStats.span(DiaryMath.clockSpread(data.values(3,-1)))+" · dispersão em torno do horário médio circular.");
        field("Dias sem noite vinculada",data.independentDays+" avaliações de sonolência");
        sub("Configurações no momento da exportação");
        field("Meta e latência inicial",DiaryStats.span(DiaryStats.number(cfg,"goal"))+" de sono; "+DiaryStats.span(DiaryStats.number(cfg,"latency"))+" de latência");
        field("Horários habituais",cfgClock(cfg,"bedMinute")+" para deitar; "+cfgClock(cfg,"wakeMinute")+" para despertar");
        field("Antecipação máxima",DiaryStats.span(DiaryStats.number(cfg,"window")));
        field("Soneca do alarme",DiaryStats.span(DiaryStats.number(cfg,"snooze")));
        field("Personalização",cfg.optBoolean("personal",true)?"Ativada":"Desativada");
        if(!data.nights.isEmpty()) {
            graph(data,0,"Duração estimada por noite (min)");
            graph(data,6,"Cansaço informado ao despertar (0–4)");
        }
        newPage();title("Indicadores e padrões");
        paragraph("Grupos pelo dia do despertar. Quando o despertar está ausente, usa-se o dia seguinte ao deitar para agrupar a noite. Dias úteis correspondem a segunda a sexta. N é a quantidade de noites que contribuíram para cada indicador.");
        float[] statsX={LEFT,232,330,415,512};
        String[] heads={"Indicador","Geral","Dias úteis","Fim de semana","N"};
        tableRow(heads,statsX,true);
        for(int i=0;i<DiaryStats.TITLES.length;i++){
            if(ensure(20)) tableRow(heads,statsX,true);
            tableRow(new String[]{DiaryStats.TITLES[i],data.aggregate(i,-1),data.aggregate(i,0),data.aggregate(i,1),String.valueOf(data.values(i,-1).size())},statsX,false);
        }
        sub("Leitura dos resultados");
        paragraph("Horários típicos são medianas. Os demais indicadores apresentam médias ou proporções. Qualidade varia de 1 a 5 e cansaço de 0 a 4. Cefaleia é o percentual das respostas com intensidade leve, moderada ou intensa. Sonolência é a média, por noite, das avaliações diurnas na escala própria de 1 a 5.");
        paragraph("A categoria de quatro ou mais despertares conta como quatro no resumo numérico. O percentual de meta atingida usa somente noites classificáveis. Um limite superior abaixo da meta permite classificar a noite como insuficiente; um limite superior acima ou igual à meta mantém o resultado indeterminado.");
        paragraph(pattern(data));
        if(!data.nights.isEmpty()) {
            newPage();title("Tabela cronológica");
            paragraph("Datas locais de referência. Horários em 24 h. Duração em minutos, com ≤ para limite superior. Q = qualidade subjetiva, de 1 a 5. O traço indica dado ausente.");
            String[] chronological={"Data","Deitar","1º alarme","Acordar","Levantar","Sono (min)","Q"};
            float[] xs={LEFT,116,178,251,319,397,516};
            tableRow(chronological,xs,true);
            for(JSONObject n:data.nights){
                if(ensure(20)) tableRow(chronological,xs,true);
                Double d=DiaryStats.sleep(n),q=DiaryStats.bounded(n,"quality",1,5);
                tableRow(new String[]{DiaryStats.date(n),DiaryStats.clock(n.optLong("bed"),n),DiaryStats.clock(n.optLong("firstRing"),n),
                    DiaryStats.clock(n.optLong("wake"),n),DiaryStats.clock(n.optLong("up"),n),
                    d==null?"—":(DiaryStats.upper(n)?"≤ ":"")+DiaryStats.num(d),q==null?"—":DiaryStats.num(q)},xs,false);
            }
            newPage();title("Dados de cada noite");
            for(JSONObject n:data.nights){
                sub("Noite de "+DiaryStats.date(n));
                for(String[] row:details(data,n)) field(row[0],row[1]);
            }
        }
        if(!data.unlinked.isEmpty()){
            sub("Sonolência sem noite vinculada");
            for(JSONObject d:data.unlinked) field(DiaryStats.fmt(d.optLong("at"),d),dayText(d));
        }
        sub("Metodologia e limitações");
        paragraph("A data principal é a de deitar. Horários detalhados incluem o deslocamento UTC. Para registros sem fuso salvo usa-se o fuso atual. Deitar pode ser marcado ao tocar o botão, digitado posteriormente ou programado. Uma sessão programada dá apenas um horário previsto para deitar. Disparos são eventos registrados pelo aplicativo. Despertar e levantar dependem da declaração do usuário.");
        paragraph("Início estimado = deitar + latência. Prioridade da latência: minutos informados, ponto representativo de uma faixa fechada, estimativa ao programar. Duração = intervalo até o despertar definitivo, descontando latência e tempo acordado à noite. Sem este último, o valor é um limite superior. Valores negativos e intervalos impossíveis ficam ausentes.");
        paragraph("Tempo na cama = levantar menos deitar. Eficiência = 100 × duração estimada / tempo na cama, somente com latência numérica autorreferida, tempo acordado informado e sequência temporal válida. A regularidade é a raiz da média das distâncias quadráticas entre horários e sua média circular. Completude considera acordar, levantar, qualidade, cansaço, cefaleia, número de despertares, tempo acordado e latência informada.");
        paragraph("O relatório não mede fases, ciclos ou estágios do sono. As respostas e os horários podem conter imprecisões. Noites omitidas, trabalho em turnos, mudanças de fuso e mudanças de rotina podem alterar as comparações. Uma associação observada não estabelece causalidade.");
        sub("Prompt para análise por IA");paragraph(AI_PROMPT);
    }
    private static String cfgClock(JSONObject cfg,String key) {
        Double d=DiaryStats.number(cfg,key);
        return d==null?DiaryStats.MISSING:DiaryStats.clockMinute(d);
    }
    static String pattern(DiaryStats s) {
        int wd=s.values(0,0).size(),we=s.values(0,1).size();
        if(wd==0||we==0) return "A comparação de duração entre dias úteis e fins de semana precisa de ao menos uma noite utilizável em cada grupo. Neste período há "+wd+" e "+we+", respectivamente.";
        return "Neste período a duração média estimada foi "+s.aggregate(0,0)+" nos dias úteis (n="+wd+") e "+s.aggregate(0,1)+" nos fins de semana (n="+we+").";
    }
    static String dayText(JSONObject d) {
        String[] levels={"","Alerta","Levemente sonolento","Sonolento","Muito sonolento","Difícil ficar acordado"};
        Double level=DiaryStats.bounded(d,"level",1,5);
        return level==null?DiaryStats.MISSING:level.intValue()+"/5 · "+levels[level.intValue()];
    }
    static List<String[]> details(DiaryStats s,JSONObject n) {
        List<String[]> rows=new ArrayList<>();
        String bedSrc=n.optString("bedSource","");
        String origin=bedSrc.equals("planned")?"programado":bedSrc.equals("manual")?"informado":bedSrc.equals("tap")?"toque registrado":"origem não informada";
        add(rows,"Deitar • "+origin,DiaryStats.fmt(n.optLong("bed"),n));
        Double lat=DiaryStats.latency(n);
        add(rows,"Início do sono • estimativa",lat==null||n.optLong("bed")<=0?DiaryStats.MISSING:DiaryStats.fmt(n.optLong("bed")+Math.round(lat*60000),n));
        add(rows,"Latência programada",DiaryStats.span(DiaryStats.number(n,"latency")));
        Double reported=DiaryStats.reported(n);
        String band=n.optString("latencyBand","");
        add(rows,"Latência autorreferida",reported!=null?DiaryStats.span(reported):band.isEmpty()||band.equals("null")?DiaryStats.MISSING:band);
        add(rows,"Latência usada no cálculo",DiaryStats.span(lat)+(lat==null?"":reported!=null?" · minutos informados":DiaryStats.number(n,"latencyBandEstimate")!=null?" · aproximação da faixa":" · estimativa do planejamento"));
        add(rows,"Recomendação de despertar",DiaryStats.fmt(n.optLong("recommended"),n));
        add(rows,"Horário-limite",DiaryStats.fmt(n.optLong("deadline"),n));
        add(rows,"Primeiro disparo • automático",DiaryStats.fmt(n.optLong("firstRing"),n));
        JSONArray rings=n.optJSONArray("rings");
        StringBuilder subsequent=new StringBuilder();
        if(rings!=null) for(int i=0;i<rings.length();i++){
            long at=rings.optLong(i);
            if(at<=0||at==n.optLong("firstRing")) continue;
            if(subsequent.length()>0) subsequent.append(", ");
            subsequent.append(DiaryStats.fmt(at,n));
        }
        add(rows,"Disparos subsequentes",subsequent.length()>0?subsequent.toString():n.optLong("firstRing")>0?"Nenhum registrado":DiaryStats.MISSING);
        add(rows,"Despertar • declarado",DiaryStats.fmt(n.optLong("wake"),n));
        add(rows,"Saída da cama • declarada",DiaryStats.fmt(n.optLong("up"),n));
        add(rows,"Tempo na cama • calculado",DiaryStats.span(DiaryStats.interval(n,"bed","up")));
        add(rows,"Duração do sono • estimada",DiaryStats.durationText(n));
        add(rows,"Meta individual desta noite",DiaryStats.span(DiaryStats.number(n,"goal")));
        add(rows,"Eficiência • estimada",pct(s.metric(n,13)));
        JSONArray zs=n.optJSONArray("snoozes");
        StringBuilder desc=new StringBuilder();
        if(zs!=null && zs.length()>0) {
            double total=0;boolean valid=true;
            for(int i=0;i<zs.length();i++) {double val=zs.optDouble(i,-1); if(i>0) desc.append(", "); if(val<0) {desc.append("?");valid=false;} else {desc.append(DiaryStats.num(val));total+=val;}}
            desc.append(" min por soneca");if(valid) desc.append(" · total ").append(DiaryStats.span(total));
        }
        add(rows,"Sonecas • uso registrado",DiaryStats.num(DiaryStats.snoozeCount(n))+(desc.length()>0?" · "+desc:""));
        add(rows,"Despertares • autorrelato",DiaryStats.wakes(n));
        add(rows,"Tempo acordado • autorrelato",DiaryStats.span(DiaryStats.number(n,"waso")));
        add(rows,"Qualidade • autorrelato",DiaryStats.option(n,"quality",DiaryStats.QUALITY,1));
        add(rows,"Cansaço • autorrelato",DiaryStats.option(n,"fatigue",DiaryStats.FATIGUE,0));
        add(rows,"Cefaleia • autorrelato",DiaryStats.option(n,"headache",DiaryStats.HEADACHE,0));
        if(s.day(n).isEmpty()) add(rows,"Sonolência diurna",DiaryStats.MISSING);
        for(JSONObject d:s.day(n)) add(rows,"Sonolência • autorrelato",DiaryStats.fmt(d.optLong("at"),d)+" · "+dayText(d));
        String notes=n.optString("notes","").trim();
        add(rows,"Observações",notes.isEmpty()||notes.equals("null")?DiaryStats.MISSING:notes);
        JSONArray edits=n.optJSONArray("edits");
        if(n.optLong("edited")>0) add(rows,"Última alteração manual",DiaryStats.fmt(n.optLong("edited"),n));
        if(edits!=null) for(int i=0;i<edits.length();i++){
            JSONObject e=edits.optJSONObject(i);
            if(e==null)continue;
            String f=e.optString("field");
            add(rows,"Alteração manual",DiaryStats.fmt(e.optLong("at"),n)+" · "+fieldName(f)+": "+editValue(f,e,"from",n)+" → "+editValue(f,e,"to",n));
        }
        return rows;
    }
    private static String fieldName(String key) {
        String[] keys={"bed","wake","up","quality","fatigue","headache","awakenings","waso","reportedLatency","latencyBand","notes","bedSource","latencyBandEstimate"};
        String[] titles={"deitar","despertar","levantar","qualidade","cansaço","cefaleia","despertares","tempo acordado","latência em minutos","faixa de latência","observações","origem de deitar","estimativa da faixa"};
        for(int i=0;i<keys.length;i++) if(keys[i].equals(key)) return titles[i];
        return key;
    }
    private static String editValue(String f,JSONObject e,String side,JSONObject n) {
        if(e.isNull(side)) return DiaryStats.MISSING;
        if(f.equals("bed")||f.equals("wake")||f.equals("up")) return DiaryStats.fmt(e.optLong(side),n);
        return e.optString(side,"—");
    }
    private static void add(List<String[]> rows,String key,String value){rows.add(new String[]{key,value});}
    private static String pct(Double d){return d==null?DiaryStats.MISSING:DiaryStats.num(d)+"%";}
}
