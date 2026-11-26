package br.app.nox;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/** Night routine, assisted planning and self-reported diary. */
public final class SleepPages {
    final MainActivity m;
    static final String[] QUALITY={"Péssima","Ruim","Regular","Boa","Excelente"};
    static final String[] FATIGUE={"Nenhum","Leve","Moderado","Intenso","Muito intenso"};
    static final String[] HEADACHE={"Ausente","Leve","Moderada","Intensa"};
    static final String[] AWAKES={"Nenhum","Um","Dois","Três","Quatro ou mais","Não me lembro"};
    static final String[] LATENCY={"Até 5 min","6 a 15 min","16 a 30 min","31 a 60 min","Mais de 60 min","Valor personalizado","Não sei"};
    static final String[] DAY={"Alerta","Levemente sonolento","Sonolento","Muito sonolento","Difícil ficar acordado"};
    final ZoneId zone() {return ZoneId.systemDefault();}
    public SleepPages(MainActivity m) { this.m=m; }
    static int safe(JSONObject j,String key,int def,int min,int max) {
        return Math.max(min, Math.min(max,j.optInt(key,def)));
    }
    static Integer value(JSONObject j,String k) {return j.isNull(k)?null:j.optInt(k);}
    static String clock(int min) {return String.format(Locale.ROOT,"%02d:%02d",min/60,min%60);}
    static String hours(int minutes) {return String.format(Locale.ROOT,"%dh%02d",minutes/60,minutes%60);}
    static String stamp(long t, ZoneId z) {
        return DateTimeFormatter.ofPattern("dd/MM • HH:mm",new Locale("pt","BR")).format(Instant.ofEpochMilli(t).atZone(z));
    }
    int wakeMinute(JSONObject c) {return safe(c,"wakeMinute",420,0,1439);}
    int bedMinute(JSONObject c) {return safe(c,"bedMinute",1350,0,1439);}
    long deadline(long bed,JSONObject c) {return Sleep.nextTime(bed,wakeMinute(c),zone());}
    Sleep.Profile profile(JSONObject c,long deadline) {
        List<Sleep.Entry> entries=new ArrayList<>();
        for(JSONObject n:m.store.all("night")) {
            long wake=n.optLong("wake"), bed=n.optLong("bed");
            if(wake<=bed || bed<=0 || wake-bed>24*60*Sleep.MIN || "planned".equals(n.optString("bedSource"))) continue;
            ZoneId z=zone();
            try{z=ZoneId.of(n.optString("zone",z.getId()));}catch(DateTimeException ignored){}
            Sleep.Entry e=new Sleep.Entry();
            e.wake=wake;
            e.weekend=Instant.ofEpochMilli(wake).atZone(z).getDayOfWeek().getValue()>=6;
            e.minute=Sleep.wall(wake,z);
            e.latency=value(n,"reportedLatency");
            if(e.latency==null) e.latency=value(n,"latencyBandEstimate");
            e.quality=value(n,"quality"); e.fatigue=value(n,"fatigue");
            Integer waso=value(n,"waso");
            if(waso!=null && e.latency!=null) {
                int min=(int)((wake-bed)/Sleep.MIN)-e.latency-waso;
                if(min>=0)e.sleepMinutes=min;
            }
            entries.add(e);
        }
        return Sleep.profile(entries,safe(c,"latency",20,0,360),wakeMinute(c),deadline,zone(),
            c.optBoolean("personal",true),value(c,"ownWake"),value(c,"ownLatency"));
    }
    List<Sleep.Option> options(long bed,long limit,JSONObject c,Sleep.Profile p) {
        return Sleep.options(bed,limit,safe(c,"goal",480,1,1440),safe(c,"window",15,0,60),p,zone());
    }
    JSONObject active() {
        JSONObject last=null;
        for(JSONObject n:m.store.all("night")) {
            if(n.optLong("up")>0 || n.optBoolean("closed") || n.optLong("bed")<=0) continue;
            if(last==null || last.optLong("bed")<n.optLong("bed")) last=n;
        }
        return last;
    }
    void set(JSONObject c,String k,Object v) {J.put(c,k,v);m.saveConfig(c);}
    void pickTime(String title,int minute,IntConsumer accept) {
        TimePickerDialog dlg=new TimePickerDialog(m,(v,h,i)->accept.accept(h*60+i),minute/60,minute%60,true);
        dlg.setTitle(title);dlg.show();
    }
    void number(String title,int initial,int min,int max,IntConsumer accept) {
        EditText input=new EditText(m); input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(initial));input.selectAll();
        AlertDialog dialog=new AlertDialog.Builder(m).setTitle(title).setMessage(min+" a "+max+" minutos")
            .setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create();
        dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{
            try{int n=Integer.parseInt(input.getText().toString().trim());if(n<min||n>max)throw new NumberFormatException();
                accept.accept(n);dialog.dismiss();}
            catch(NumberFormatException ex){input.setError("Informe um valor entre "+min+" e "+max+".");}
        }));dialog.show();
    }
    void select(String title,String[] items,int current,IntConsumer action) {
        new AlertDialog.Builder(m).setTitle(title).setSingleChoiceItems(items,current,(d,i)->{d.dismiss();action.accept(i);})
            .setNegativeButton("Voltar",null).show();
    }
    void confirm(String title,String body,String yes,Runnable action) {
        new AlertDialog.Builder(m).setTitle(title).setMessage(body).setNegativeButton("Voltar",null)
            .setPositiveButton(yes,(d,w)->action.run()).show();
    }
    void dateTime(String title,long epoch, ZoneId z, LongConsumer accept) {
        ZonedDateTime initial=Instant.ofEpochMilli(epoch).atZone(z);
        new DatePickerDialog(m,(date,y,mo,d)->{
            TimePickerDialog time=new TimePickerDialog(m,(v,h,min)->accept.accept(
                LocalDate.of(y,mo+1,d).atTime(h,min).atZone(z).toInstant().toEpochMilli()),
                initial.getHour(),initial.getMinute(),true);time.setTitle(title);time.show();
        },initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();
    }
    public void show() {
        JSONObject c=m.config(), n=active();
        m.page("Sono","Sua rotina de hoje",2);
        if(n!=null) {
            boolean planned="planned".equals(n.optString("bedSource"));
            m.heading(planned?"Noite planejada":"Noite em andamento");
            m.note("Deitar • "+stamp(n.optLong("bed"),zone())+"\nAlarme • "+stamp(n.optLong("recommended"),zone()));
            if(planned && n.optLong("firstRing")==0) m.button("Vou dormir agora",this::startNow);
            if(n.optLong("bed")<=System.currentTimeMillis() && n.optLong("wake")==0)
                m.button("Despertei agora",()->finishNight(n,false));
            if(n.optLong("bed")<=System.currentTimeMillis())
                m.button("Levantei agora",()->finishNight(n,true));
            m.card("Encerrar sessão sem informar horários","O registro fica disponível para preencher depois.",()->confirm(
                "Encerrar esta sessão?","O alarme desta noite será cancelado. Os horários já registrados serão preservados.",
                "Encerrar",()->{Scheduler.remove(m,"sleep-"+n.optString("id"));J.put(n,"closed",true);m.store.put("night",n);show();}));
        } else {
            m.heading("Pronto para dormir?");
            m.note("Ao tocar no botão, o Nox registra o horário e agenda seu despertar.");
            m.button("Vou dormir agora",this::startNow);
        }
        m.card("Planejar meu sono","Deitar às "+clock(bedMinute(c))+"  ·  Limite "+clock(wakeMinute(c)),this::plan);
        m.card("Sonolência durante o dia","Registrar como você se sente agora",this::day);
        m.card("Minha rotina aprendida","Ver dados e ajustar a personalização",this::learned);
        m.card("Lembretes opcionais","Escolher os horários das notificações",this::reminders);
    }
    void finishNight(JSONObject n,boolean up) {
        long now=System.currentTimeMillis();
        if(n.optLong("bed")>now){m.message("O horário de deitar ainda está no futuro.");return;}
        String id=n.optString("id");
        Scheduler.remove(m,"sleep-"+id);
        // The alarm service owns its active player. An explicit stop is scoped to this occurrence.
        JSONObject fresh=m.store.get("night",id);
        if(fresh==null)return;
        if(fresh.optLong("wake")==0)J.put(fresh,"wake",now);
        if(up && fresh.optLong("up")==0)J.put(fresh,"up",now);
        m.store.put("night",fresh);
        if(up)morning(id);else show();
    }
    public void startNow() {
        JSONObject c=m.config();long bed=System.currentTimeMillis(), limit=deadline(bed,c);
        Sleep.Profile p=profile(c,limit);
        if(c.optBoolean("assist",false)) {
            offer(bed,limit,c,p,false); return;
        }
        apply(bed,limit,c,p,options(bed,limit,c,p).get(0),false);
    }
    String optionText(Sleep.Option o,int goal) {
        String adequacy=o.minutes<goal?"Abaixo da meta":o.minutes==goal?"Na meta":"Acima da meta";
        return hours(o.minutes)+" previstos  ·  "+adequacy+"\n"+(o.early==0?"No horário-limite":o.early+" min antes do limite")
          +"  ·  ≈ "+String.format(new Locale("pt","BR"),"%.1f",o.cycles)+" ciclos teóricos\n"+o.why;
    }
    void offer(long bed,long limit,JSONObject c,Sleep.Profile p,boolean planned) {
        m.page("Escolher despertar","Limite às "+stamp(limit,zone()),2);
        m.note("Ciclos são aproximações. A hora real de adormecer e os despertares podem alterar a duração.");
        List<Sleep.Option> opts=options(bed,limit,c,p);
        for(int i=0;i<opts.size();i++){
            Sleep.Option o=opts.get(i);
            m.card(clock(Sleep.wall(o.at,zone()))+(i==0?"  ·  Recomendado":""),optionText(o,c.optInt("goal",480)),
                ()->apply(bed,limit,c,p,o,planned));
        }
    }
    void apply(long bed,long limit,JSONObject c,Sleep.Profile p,Sleep.Option o,boolean planned) {
        if(!Scheduler.exact(m)){m.message("Autorize alarmes exatos para iniciar sua noite.");m.diagnostics();return;}
        if(o.at<=System.currentTimeMillis()){m.message("Esse horário já passou. Recalcule a programação.");plan();return;}
        JSONObject existing=active();
        if(existing!=null && (!"planned".equals(existing.optString("bedSource")) || existing.optLong("firstRing")>0)){
            m.message("Já existe uma noite em andamento. Encerre a sessão para iniciar outra.");show();return;
        }
        String id=existing!=null?existing.optString("id"):UUID.randomUUID().toString();
        JSONObject n=J.obj("id",id,"bed",bed,"bedSource",planned?"planned":"tap","zone",zone().getId(),
            "recommended",o.at,"deadline",limit,"goal",c.optInt("goal",480),"latency",p.latency,
            "rings",new JSONArray(),"snoozes",new JSONArray());
        JSONObject a=J.obj("id","sleep-"+id,"enabled",true,"name","Despertar Nox","days",0,"at",o.at,
            "minute",Sleep.wall(o.at,zone()),"deadline",limit,"night",id,"tone",c.optString("tone","nox"),
            "volume",c.optInt("volume",80),"ramp",c.optBoolean("ramp",true),"vibrate",c.optBoolean("vibrate",true),
            "snooze",c.optInt("snooze",5),"maxSnoozes",c.optInt("maxSnoozes",0));
        try{
            m.store.put("night",n);
            try{Scheduler.put(m,a);}catch(RuntimeException err){
                if(existing!=null)m.store.put("night",existing);else m.store.remove("night",id);
                throw err;
            }
            m.page(planned?"Noite planejada":"Boa noite.","Despertador agendado no Android",2);
            m.heading(clock(Sleep.wall(o.at,zone())));
            m.note(stamp(o.at,zone())+"  ·  "+hours(o.minutes)+" de sono previsto\nLimite às "+clock(Sleep.wall(limit,zone()))+".");
            if(planned)m.note("Deitar é um horário previsto. Ao se deitar, use “Vou dormir agora” para registrar a hora e recalcular.");
            m.button("Concluir",m::home);
        }catch(RuntimeException e){m.message("Falha ao agendar o despertar. Confira as permissões e tente de novo.");m.diagnostics();}
    }
    public void plan() {
        JSONObject c=m.config();long now=System.currentTimeMillis();
        long bed=Sleep.nextTime(now,bedMinute(c),zone()), limit=deadline(bed,c);
        Sleep.Profile p=profile(c,limit);
        List<Sleep.Option> os=options(bed,limit,c,p);int goal=c.optInt("goal",480);
        m.page("Alarme inteligente","Planeje seu próximo período de sono",2);
        m.card("Preciso estar acordado às",clock(wakeMinute(c)),()->pickTime("Horário-limite",wakeMinute(c),x->{set(c,"wakeMinute",x);plan();}));
        m.card("Vou me deitar às",clock(bedMinute(c)),()->pickTime("Horário de deitar",bedMinute(c),x->{set(c,"bedMinute",x);plan();}));
        m.card("Meta de sono",hours(goal),()->number("Duração desejada",goal,30,960,x->{set(c,"goal",x);plan();}));
        m.card("Latência inicial",c.optInt("latency",20)+" min  ·  usada nesta noite: "+p.latency+" min",
            ()->number("Minutos para adormecer",c.optInt("latency",20),0,360,x->{set(c,"latency",x);plan();}));
        int[] wins={0,5,10,15,20,30,45,60};String[] names={"Não antecipar","Até 5 min","Até 10 min","Até 15 min","Até 20 min","Até 30 min","Até 45 min","Até 60 min"};
        int w=c.optInt("window",15);int wi=Arrays.binarySearch(wins,w);
        m.card("Janela de antecipação",w+" min",()->select("Antecipar o despertar",names,wi,i->{set(c,"window",wins[i]);plan();}));
        boolean assist=c.optBoolean("assist",false);
        m.card("Escolha do horário",assist?"Manual assistida":"Automática",
            ()->select("Escolha do horário",new String[]{"Automática","Manual assistida"},assist?1:0,i->{set(c,"assist",i==1);plan();}));
        m.heading("Despertar recomendado • "+clock(Sleep.wall(os.get(0).at,zone())));
        m.note(optionText(os.get(0),goal));
        m.note("Deitar previsto • "+stamp(bed,zone())+"\nDespertar • "+stamp(os.get(0).at,zone()));
        m.button(assist?"Escolher e agendar":"Agendar esta noite",()->{if(assist)offer(bed,limit,c,p,true);else apply(bed,limit,c,p,os.get(0),true);});
        m.card("Quando devo me deitar?","Calcular a partir do próximo horário-limite",()->{
            long dl=Sleep.nextTime(System.currentTimeMillis(),wakeMinute(c),zone());
            Sleep.Profile pr=profile(c,dl);
            long latest=dl-(goal+pr.latency)*Sleep.MIN;
            long earlier=latest-30*Sleep.MIN;
            new AlertDialog.Builder(m).setTitle("Horários para se deitar").setMessage(
                "Para despertar em "+stamp(dl,zone())+"\n\n"+stamp(latest,zone())+" • estimativa para sua meta de "+hours(goal)
                +"\n"+stamp(earlier,zone())+" • reserva de 30 minutos\n\nInclui "+pr.latency+" min de latência estimada."
                +(latest<System.currentTimeMillis()?" O horário para atingir a meta já passou.":"")).setPositiveButton("Entendi",null).show();
        });
    }

    public void learned(){
        JSONObject c=m.config();long now=System.currentTimeMillis();
        Sleep.Profile p=profile(c,deadline(now,c));boolean enabled=c.optBoolean("personal",true);
        m.page("Minha rotina aprendida","Dados dos últimos 90 dias",2);
        m.card("Personalização",enabled?"Ativada":"Desativada",()->{set(c,"personal",!enabled);learned();});
        if(enabled){
            m.note(p.weekendGroup?"Base: noites que terminam no mesmo tipo de dia (útil ou fim de semana).":"Base: dias úteis e fins de semana agrupados.");
            m.note("Latência usada: "+p.latency+" min. Relatos de latência: "+p.nLatency+".");
            m.note(p.typical==null?"Ainda não há um horário regular de despertar definido.":"Despertar de referência: "+clock(p.typical)+".");
            m.note(p.good==null?"Ainda não há sete manhãs com boa qualidade e pouco cansaço no grupo.":"Despertar mediano nas manhãs bem avaliadas: "+clock(p.good)+".");
            if(p.spread!=null)m.note("Desvio absoluto mediano do despertar: "+p.spread+" min.");
            if(p.duration!=null)m.note("Duração mediana nas noites com latência e vigília informadas: "+hours(p.duration)+".");
            if(p.nWake<Sleep.MIN_SAMPLE || p.nLatency<Sleep.MIN_SAMPLE)m.note("Dados insuficientes em parte dos indicadores. O Nox mantém as configurações iniciais onde faltam observações.");
            m.card("Ajustar a latência usada",c.has("ownLatency")?"Ajuste manual":"Cálculo pelo histórico",
                ()->number("Latência do perfil",p.latency,0,360,x->{set(c,"ownLatency",x);learned();}));
            m.card("Ajustar horário de referência",c.has("ownWake")?"Ajuste manual":"Cálculo pelo histórico",
                ()->pickTime("Despertar de referência",p.typical!=null?p.typical:wakeMinute(c),x->{set(c,"ownWake",x);learned();}));
        }
        m.button("Restaurar parâmetros iniciais",()->confirm("Restaurar parâmetros?","Voltar à latência de 20 minutos, remover ajustes manuais e desativar o aprendizado. O diário será preservado.","Restaurar",()->{
            c.remove("ownLatency");c.remove("ownWake");J.put(c,"latency",20);set(c,"personal",false);learned();
        }));
    }
    void record(JSONObject n,String field,Object val) {
        Object before=n.isNull(field)?null:n.opt(field);
        if(Objects.equals(before,val))return;
        if(val==null)n.remove(field);else J.put(n,field,val);
        long at=System.currentTimeMillis();
        if(before!=null || field.equals("bed") || field.equals("wake") || field.equals("up")){
            JSONArray edits=n.optJSONArray("edits");if(edits==null)edits=new JSONArray();
            edits.put(J.obj("at",at,"field",field,"from",before==null?JSONObject.NULL:before,"to",val==null?JSONObject.NULL:val));
            J.put(n,"edits",edits);J.put(n,"edited",at);
        }
    }
    void update(String id,String key,Object val) {
        JSONObject fresh=m.store.get("night",id);if(fresh!=null){record(fresh,key,val);m.store.put("night",fresh);}
    }
    interface OnChoice {void chosen(int index);}
    void choices(String title,String[] items,int selected,OnChoice bind) {
        m.heading(title);
        HorizontalScrollView scroll=new HorizontalScrollView(m);scroll.setHorizontalScrollBarEnabled(true);
        RadioGroup group=new RadioGroup(m);group.setOrientation(LinearLayout.HORIZONTAL);scroll.addView(group);
        for(int i=0;i<items.length;i++) {
            int index=i;
            RadioButton b=new RadioButton(m);b.setId(View.generateViewId());b.setText(items[i]);
            b.setMinHeight(m.dp(48));b.setPadding(0,0,m.dp(10),0);group.addView(b);b.setChecked(i==selected);
            b.setOnClickListener(v->bind.chosen(index));
        }
        m.body.addView(scroll);
    }
    public void morning(String id) {
        JSONObject n=m.store.get("night",id);
        if(n==null){m.message("Registro indisponível.");show();return;}
        m.page("Como foi seu despertar?",stamp(n.optLong("bed"),zone())+"  ·  Respostas opcionais",2);
        m.note("Toque para salvar cada resposta. Você pode completar depois.");
        choices("Qualidade do sono",QUALITY,n.optInt("quality",0)-1,i->update(id,"quality",i+1));
        choices("Cansaço ao despertar",FATIGUE,n.optInt("fatigue",-1),i->update(id,"fatigue",i));
        choices("Dor de cabeça",HEADACHE,n.optInt("headache",-1),i->update(id,"headache",i));
        int wakes=n.optInt("awakenings",-2);
        choices("Despertares noturnos",AWAKES,wakes==-1?5:wakes,i->update(id,"awakenings",i==5?-1:i));
        int idx=n.has("reportedLatency")?5:Arrays.asList(LATENCY).indexOf(n.optString("latencyBand"));
        choices("Tempo para adormecer",LATENCY,idx,i->{
            if(i==5){
                number("Latência em minutos",n.optInt("reportedLatency",n.optInt("latency",20)),0,720,x->{
                    saveLatency(id,null,x,null);morning(id);
                });
            }else{Integer estimate=i<4?Integer.valueOf(new int[]{3,10,23,45}[i]):null;
                saveLatency(id,LATENCY[i],null,estimate);}
        });
        m.card("Tempo total acordado durante a noite",n.has("waso")?n.optInt("waso")+" min":"Não informado",
            ()->number("Tempo acordado",n.optInt("waso",0),0,1440,x->{
                JSONObject fresh=m.store.get("night",id);if(fresh==null)return;
                int lat=fresh.optInt("reportedLatency",fresh.optInt("latencyBandEstimate",fresh.optInt("latency",0)));
                if(fresh.optLong("wake")>0 && x+lat>(fresh.optLong("wake")-fresh.optLong("bed"))/Sleep.MIN){
                    m.message("O tempo acordado mais a latência excede o intervalo até o despertar.");return;
                }
                update(id,"waso",x);morning(id);}));
        m.card("Horários e observações","Conferir, corrigir ou completar o registro",()->details(id));
        m.button("Concluir",m::home);
    }
    void saveLatency(String id,String band,Integer exact,Integer approx) {
        JSONObject n=m.store.get("night",id);if(n==null)return;
        Integer check=exact!=null?exact:approx;
        if(check!=null && n.optLong("wake")>0 && check+n.optInt("waso",0)>(n.optLong("wake")-n.optLong("bed"))/Sleep.MIN){
            m.message("A latência mais o tempo acordado excede o intervalo até o despertar.");morning(id);return;
        }
        record(n,"latencyBand",band);record(n,"reportedLatency",exact);record(n,"latencyBandEstimate",approx);m.store.put("night",n);
    }
    public void details(String id) {
        JSONObject n=m.store.get("night",id);if(n==null){m.history();return;}
        ZoneId original;
        try{original=ZoneId.of(n.optString("zone",zone().getId()));}catch(DateTimeException e){original=zone();}
        final ZoneId z=original;
        m.page("Detalhes da noite",stamp(n.optLong("bed"),z)+"  ·  "+z,2);
        boolean fixed=n.optLong("up")>0 || n.optBoolean("closed") || n.optLong("firstRing")>0;
        if(!fixed)m.note("Durante uma sessão, registre o despertar e a saída da cama na tela Sono. Os horários ficam editáveis depois.");
        String[] keys={"bed","wake","up"}, labels={"Deitar","Despertar definitivo","Saída da cama"};
        for(int i=0;i<keys.length;i++){
            String field=keys[i], name=labels[i];long old=n.optLong(field);
            String src=field.equals("bed")?"  ·  "+("planned".equals(n.optString("bedSource"))?"planejado":"manual".equals(n.optString("bedSource"))?"corrigido":"registro ao tocar"):"";
            m.card(name,(old>0?stamp(old,z):"Não informado")+src,()->{
                if(!fixed){m.message("Conclua a sessão para corrigir os horários.");return;}
                dateTime(name,old>0?old:Math.max(n.optLong("bed"),Math.min(System.currentTimeMillis(),n.optLong("deadline",System.currentTimeMillis()))),z,
                    t->{
                        JSONObject fresh=m.store.get("night",id);if(fresh==null)return;
                        long bed=field.equals("bed")?t:fresh.optLong("bed"),wake=field.equals("wake")?t:fresh.optLong("wake"),up=field.equals("up")?t:fresh.optLong("up");
                        int latency=fresh.optInt("reportedLatency",fresh.optInt("latencyBandEstimate",0));
                        if(t>System.currentTimeMillis() || (wake>0 && (wake<bed || wake-bed>24*60*Sleep.MIN || wake-bed<(latency+fresh.optInt("waso",0))*Sleep.MIN)) ||
                             (up>0 && (up<bed || wake>0&&up<wake || up-bed>24*60*Sleep.MIN))){
                            m.message("Confira a data e a sequência de deitar, despertar e levantar.");return;
                        }
                        record(fresh,field,t);if(field.equals("bed"))record(fresh,"bedSource","manual");
                        m.store.put("night",fresh);details(id);
                    });
            });
        }
        m.card("Observações",n.optString("notes","Adicionar nota opcional"),()->{
            EditText input=new EditText(m);input.setText(n.optString("notes"));input.setMinLines(2);
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
            new AlertDialog.Builder(m).setTitle("Observações da noite").setView(input).setNegativeButton("Cancelar",null)
                .setPositiveButton("Salvar",(d,w)->{String s=input.getText().toString().trim();update(id,"notes",s.isEmpty()?null:s);details(id);}).show();
        });
        m.button("Editar avaliação matinal",()->morning(id));
        m.card("Limpar uma resposta","Voltar a marcar um dado como ausente",()->{
            String[] fields={"quality","fatigue","headache","awakenings","waso","latency","notes"};
            String[] texts={"Qualidade","Cansaço","Cefaleia","Despertares","Tempo acordado","Latência relatada","Observações"};
            select("Limpar resposta",texts,-1,i->{
                if(fields[i].equals("latency"))saveLatency(id,null,null,null);
                else update(id,fields[i],null);details(id);
            });
        });
        m.button("Histórico",m::history);
    }
    public void diary(String id){morning(id);}
    public void day() {
        m.page("Sonolência diurna","Como você está se sentindo agora?",2);
        m.note("Escala subjetiva do Nox, de 1 a 5. Cada resposta recebe data e hora.");
        for(int i=0;i<DAY.length;i++){
            int level=i+1;
            m.button(level+"   "+DAY[i],()->{
                long now=System.currentTimeMillis(),newest=0;
                String night=null;
                for(JSONObject n:m.store.all("night")){
                    long wake=n.optLong("wake");
                    if(wake>newest && wake<=now && now-wake<=30*60*Sleep.MIN){newest=wake;night=n.optString("id");}
                }
                JSONObject item=J.obj("id",UUID.randomUUID().toString(),"at",now,"level",level);
                if(night!=null)J.put(item,"night",night);
                m.store.put("day",item);m.message("Sonolência registrada às "+clock(Sleep.wall(now,zone()))+".");m.home();
            });
        }
    }
    public void reminders() {
        JSONObject c=m.config();m.page("Lembretes","Notificações opcionais da rotina",2);
        reminder(c,"bed","Rotina noturna",bedMinute(c));
        reminder(c,"day","Sonolência diurna",14*60);
        m.note("Cada lembrete ativo gera até uma notificação por dia. O Android pode adiar a entrega durante a economia de bateria.");
        m.card("Permissões de notificação","Consultar configurações do aparelho",m::diagnostics);
    }
    void reminder(JSONObject c,String k,String label,int def){
        String on=k+"Reminder",hour=k+"ReminderMinute";
        boolean enabled=c.optBoolean(on,false);int t=c.optInt(hour,def);
        m.card(label,enabled?"Ativado • "+clock(t):"Desativado",()->{
            String[] actions=enabled?new String[]{"Alterar horário","Desativar"}:new String[]{"Ativar e escolher horário"};
            select(label,actions,-1,i->{
                if(enabled&&i==1){set(c,on,false);ReminderReceiver.sync(m);reminders();}
                else pickTime(label,t,x->{J.put(c,hour,x);set(c,on,true);ReminderReceiver.sync(m);reminders();});
            });
        });
    }
}
