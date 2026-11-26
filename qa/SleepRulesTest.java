package br.app.nox;

import java.time.*;
import java.util.*;

/** Run on a JDK: javac -d out app/src/main/java/br/app/nox/Sleep.java qa/SleepRulesTest.java
 *                 java -cp out br.app.nox.SleepRulesTest */
public final class SleepRulesTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static long at(String s){return Instant.parse(s).toEpochMilli();}
    public static void main(String[] args){
        ZoneId utc=ZoneOffset.UTC, ny=ZoneId.of("America/New_York");
        check(Sleep.nextTime(at("2025-03-09T05:00:00Z"),150,ny)==at("2025-03-09T07:00:00Z"),"hora inexistente");
        check(Sleep.nextTime(at("2025-11-02T05:45:00Z"),90,ny)==at("2025-11-02T06:30:00Z"),"hora civil repetida");
        check(Sleep.nextTime(at("2025-01-01T23:50:00Z"),10,utc)==at("2025-01-02T00:10:00Z"),"meia-noite");
        check(Sleep.circular(3,1438)==5,"desvio circular");
        long limit=at("2025-11-21T07:00:00Z");
        List<Sleep.Entry> entries=new ArrayList<>();
        for(int i=0;i<10;i++){
            Sleep.Entry e=new Sleep.Entry();e.wake=limit-(i+1)*24*60*Sleep.MIN;e.latency=i==9?350:12;
            e.minute=i==9?600:405;e.weekend=false;e.quality=4;e.fatigue=1;entries.add(e);
        }
        Sleep.Profile p=Sleep.profile(entries,20,420,limit,utc,true,null,null);
        check(p.latency==12 && p.typical==405 && p.good==405,"medianas robustas");
        check(Sleep.profile(entries.subList(0,6),20,420,limit,utc,true,null,null).typical==null,"poucos dados");
        check(Sleep.profile(entries.subList(0,6),20,420,limit,utc,true,null,null).latency==20,"latência inicial");
        check(Sleep.profile(entries,20,420,limit,utc,false,390,55).latency==20,"personalização desligada");
        check(Sleep.profile(entries,20,420,limit,utc,true,390,55).latency==55,"latência manual");
        check(Sleep.profile(entries,20,420,limit,utc,true,390,55).typical==390,"referência manual");
        long bed=limit-540*Sleep.MIN;
        check(Sleep.options(bed,limit,480,60,p,utc).get(0).early==15,"preserva rotina");
        check(Sleep.options(limit-460*Sleep.MIN,limit,480,60,p,utc).get(0).at==limit,"sono insuficiente");
        check(Sleep.options(limit-425*Sleep.MIN,limit,350,60,p,utc).get(0).at==limit,"piso automático adulto");
        Sleep.Profile fresh=Sleep.profile(Collections.emptyList(),20,420,limit,utc,true,null,null);
        check(Sleep.options(bed,limit,480,60,fresh,utc).get(0).at==limit,"ciclos não antecipam em modo automático");
        check(Sleep.options(limit-Sleep.MIN,limit,480,60,fresh,utc).get(0).minutes==0,"oportunidade menor que latência");
        check(Sleep.options(bed,limit,480,0,p,utc).size()==1,"não antecipar");
        Random r=new Random(5831);
        for(int i=0;i<4000;i++){
            int window=r.nextInt(61),goal=30+r.nextInt(930),span=1+r.nextInt(1200);
            long start=limit-span*Sleep.MIN;
            List<Sleep.Option> os=Sleep.options(start,limit,goal,window,p,utc);
            HashSet<Long> seen=new HashSet<>();
            for(Sleep.Option o:os){
                check(o.at<=limit && o.at>start && limit-o.at<=window*Sleep.MIN,"janela e limite");
                check(o.minutes>=0 && seen.add(o.at),"duração e unicidade");
            }
            Sleep.Option chosen=os.get(0);
            check(chosen.early==0 || chosen.early<=15 && chosen.minutes>=Math.max(goal,420),"não sacrifica meta");
        }
        System.out.println("OK: "+checks+" verificações.");
    }
}
