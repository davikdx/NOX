package br.app.nox;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DiaryStats {
    static final String MISSING = "Sem dado";
    static final Locale PT = new Locale("pt", "BR");
    static final String[] TITLES = {"Duração estimada", "Tempo na cama", "Horário de deitar",
        "Despertar definitivo", "Latência informada", "Qualidade subjetiva", "Cansaço ao despertar",
        "Cefaleia presente", "Despertares noturnos", "Sonecas do alarme", "Até sair da cama",
        "Sonolência diurna média", "Meta atingida", "Eficiência estimada"};
    static final String[] UNITS = {"min","min","h","h","min","1–5","0–4","%","vezes¹","vezes","min","escala própria","%","%"};
    static final String[] QUALITY = {"","Péssima","Ruim","Regular","Boa","Excelente"};
    static final String[] FATIGUE = {"Nenhum","Leve","Moderado","Intenso","Muito intenso"};
    static final String[] HEADACHE = {"Ausente","Leve","Moderada","Intensa"};
    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", PT);
    static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm xxx", PT);
    final List<JSONObject> nights = new ArrayList<>();
    final Map<String,List<JSONObject>> days = new HashMap<>();
    int independentDays;
    final List<JSONObject> unlinked = new ArrayList<>();
    final LocalDate from, to;
    final ZoneId zone = ZoneId.systemDefault();

    DiaryStats(Store store, LocalDate from, LocalDate to) {
        this(store.all("night"),store.all("day"),from,to);
    }
    DiaryStats(List<JSONObject> nightRows,List<JSONObject> dayRows,LocalDate from, LocalDate to) {
        this.from = from;
        this.to = to;
        for (JSONObject n : nightRows) {
            long anchor = anchor(n);
            if (anchor <= 0 || anchor > System.currentTimeMillis()) continue;
            if (inside(local(anchor,n).toLocalDate())) nights.add(n);
        }
        Collections.sort(nights, Comparator.comparingLong(DiaryStats::anchor));
        for (JSONObject d : dayRows) {
            if (d.optLong("at") <= 0) continue;
            String id = d.optString("night","");
            if (id.isEmpty() || id.equals("null")) {
                if (inside(local(d.optLong("at"),d).toLocalDate())) {
                    independentDays++;
                    unlinked.add(d);
                }
            } else {
                days.computeIfAbsent(id, k -> new ArrayList<>()).add(d);
            }
        }
        Comparator<JSONObject> chronological = Comparator.comparingLong(d -> d.optLong("at"));
        for (List<JSONObject> ds : days.values()) Collections.sort(ds,chronological);
        Collections.sort(unlinked,chronological);
    }

    boolean inside(LocalDate date) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    static ZoneId zone(JSONObject n) {
        try { return ZoneId.of(n.optString("zone", ZoneId.systemDefault().getId())); }
        catch (RuntimeException e) { return ZoneId.systemDefault(); }
    }
    static ZonedDateTime local(long t, JSONObject n) { return Instant.ofEpochMilli(t).atZone(zone(n)); }
    static String fmt(long t, JSONObject n) { return t <= 0 ? MISSING : local(t,n).format(MOMENT); }
    static String clock(long t, JSONObject n) { return t <= 0 ? "—" : local(t,n).format(CLOCK); }
    static long anchor(JSONObject n) {
        for(String key:new String[]{"bed","wake","firstRing","up"}) {
            long value=n.optLong(key);
            if(value>0) return value;
        }
        return 0;
    }
    static String date(JSONObject n) { long at=anchor(n);return at<=0?MISSING:local(at,n).toLocalDate().toString(); }
    static Double number(JSONObject n,String key) {
        if (n.isNull(key)) return null;
        double d = n.optDouble(key,Double.NaN);
        return Double.isFinite(d) && d >= 0 ? d : null;
    }
    static Double bounded(JSONObject n,String key,int lo,int hi) {
        Double d=number(n,key);
        return d!=null && d>=lo && d<=hi && d==Math.rint(d) ? d : null;
    }
    static String num(Double v) { return v == null ? MISSING : String.format(PT, Math.abs(v-Math.rint(v))<0.05?"%.0f":"%.1f",v); }
    static String span(Double v) {
        if (v == null) return MISSING;
        long m = Math.round(v);
        return m < 60 ? m+" min" : String.format(PT,"%d h %02d min",m/60,m%60);
    }
    static Double reported(JSONObject n) { return number(n,"reportedLatency"); }
    static Double latency(JSONObject n) {
        Double r=reported(n);
        if(r!=null) return r;
        Double band=number(n,"latencyBandEstimate");
        return band!=null?band:number(n,"latency");
    }
    static Double sleep(JSONObject n) {
        return DiaryMath.duration(n.optLong("bed"),n.optLong("wake"),latency(n),number(n,"waso"));
    }
    static boolean upper(JSONObject n) { return number(n,"waso")==null; }
    static String durationText(JSONObject n) {
        Double d=sleep(n);
        return d==null?MISSING:(upper(n)?"até ":"")+span(d);
    }
    static Double interval(JSONObject n,String a,String b) {
        long start=n.optLong(a),end=n.optLong(b);
        return start>0 && end>=start ? (end-start)/60000d : null;
    }
    static String option(JSONObject n,String key,String[] labels,int min) {
        Double v=bounded(n,key,min,labels.length-1);
        return v==null?MISSING:labels[v.intValue()];
    }
    static String wakes(JSONObject n) {
        if (n.optInt("awakenings",-2)==-1) return "Não me lembro";
        Double count=bounded(n,"awakenings",0,4);
        return count==null?MISSING:count==4?"4 ou mais":num(count);
    }
    static Double snoozeCount(JSONObject n) {
        JSONArray a=n.optJSONArray("snoozes");
        if(a!=null) return (double)a.length();
        return n.optLong("firstRing")>0 ? Double.valueOf(0) : null;
    }
    List<JSONObject> day(JSONObject n) { return days.getOrDefault(n.optString("id"),Collections.emptyList()); }
    Double metric(JSONObject n,int type) {
        Double v;
        long t;
        switch(type) {
            case 0: return sleep(n);
            case 1: return interval(n,"bed","up");
            case 2: case 3:
                t=n.optLong(type==2?"bed":"wake");
                return t<=0 ? null : (double)(local(t,n).getHour()*60+local(t,n).getMinute());
            case 4: return reported(n);
            case 5: return bounded(n,"quality",1,5);
            case 6: return bounded(n,"fatigue",0,4);
            case 7: v=bounded(n,"headache",0,3); return v==null?null:Double.valueOf(v>0?100:0);
            case 8: return bounded(n,"awakenings",0,4);
            case 9: return snoozeCount(n);
            case 10:return interval(n,"wake","up");
            case 11:
                List<Double> ds=new ArrayList<>();
                for(JSONObject d:day(n)) { v=number(d,"level"); if(v!=null) ds.add(v); }
                return DiaryMath.mean(ds);
            case 12:
                v=sleep(n);
                Double goal=number(n,"goal");
                if(v==null||goal==null||goal<=0) return null;
                if(v<goal) return 0d;
                return upper(n)?null:Double.valueOf(100);
            case 13:return DiaryMath.efficiency(n.optLong("bed"),n.optLong("wake"),n.optLong("up"),reported(n),number(n,"waso"));
            default:throw new IllegalArgumentException("Indicador inválido");
        }
    }

    boolean weekend(JSONObject n) {
        long t=n.optLong("wake");
        LocalDate day=t>0?local(t,n).toLocalDate():n.optLong("bed")>0?local(n.optLong("bed"),n).toLocalDate().plusDays(1):local(anchor(n),n).toLocalDate();
        return day.getDayOfWeek().getValue()>=6;
    }
    List<Double> values(int type,int group) {
        List<Double> list=new ArrayList<>();
        for(JSONObject n:nights) {
            if (group>=0 && (weekend(n)?1:0)!=group) continue;
            Double v=metric(n,type);
            if(v!=null) list.add(v);
        }
        return list;
    }
    static String clockMinute(double t) {
        int m=Math.floorMod((int)Math.round(t),1440);
        return String.format(PT,"%02d:%02d",m/60,m%60);
    }
    String aggregate(int type,int group) {
        List<Double> vals=values(type,group);
        if(vals.isEmpty()) return MISSING;
        if(type==2 || type==3) {
            // Preserve both sides of midnight when reporting a typical evening bedtime.
            List<Double> rotated=new ArrayList<>();
            int boundary=type==2?720:0;
            for (double d:vals) rotated.add((d-boundary+1440)%1440);
            return clockMinute(DiaryMath.median(rotated)+boundary);
        }
        Double avg=DiaryMath.mean(vals);
        if(type==0||type==1||type==4||type==10) return span(avg);
        return num(avg)+(type==7||type==12||type==13?"%":"");
    }
    String completeness() {
        if(nights.isEmpty()) return MISSING;
        int present=0;
        for(JSONObject n:nights) {
            if(n.optLong("wake")>0) present++;
            if(n.optLong("up")>0) present++;
            if(bounded(n,"quality",1,5)!=null) present++;
            if(bounded(n,"fatigue",0,4)!=null) present++;
            if(bounded(n,"headache",0,3)!=null) present++;
            if(bounded(n,"awakenings",0,4)!=null) present++;
            if(number(n,"waso")!=null) present++;
            if(reported(n)!=null||hasBand(n)) present++;
        }
        return num(100d*present/(nights.size()*8))+"% ("+present+"/"+(nights.size()*8)+")";
    }
    static boolean hasBand(JSONObject n) {
        String s=n.optString("latencyBand","").trim();
        return !s.isEmpty()&&!s.equalsIgnoreCase("não sei")&&!s.equals("null");
    }
    int upperCount() {
        int k=0;
        for(JSONObject n:nights) if(upper(n)&&sleep(n)!=null) k++;
        return k;
    }
    String period() {
        if(from==null && to==null) {
            return nights.isEmpty()?"Histórico completo":"Histórico completo · "+date(nights.get(0))+" a "+date(nights.get(nights.size()-1));
        }
        return from+" a "+to;
    }
}
