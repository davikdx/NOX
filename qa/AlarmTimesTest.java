package br.app.nox;
import java.time.*;
public final class AlarmTimesTest {
    public static void main(String[] args) {
        long fri=Instant.parse("2026-11-27T21:00:00Z").toEpochMilli();
        long mon=Instant.parse("2026-11-30T07:00:00Z").toEpochMilli();
        assert AlarmTimes.next(420,1,fri,ZoneOffset.UTC,"")==mon;
        assert AlarmTimes.next(420,1,fri,ZoneOffset.UTC,"2026-11-30")==mon+7*86400000L;
        assert AlarmTimes.snooze(fri,5,fri+25000,0,0)==fri+25000;
        assert AlarmTimes.snooze(fri,5,fri,0,0)==0;
        assert AlarmTimes.snooze(fri,5,0,1,1)==0;
        ZoneId ny=ZoneId.of("America/New_York");
        long fold=Instant.parse("2025-11-02T05:45:00Z").toEpochMilli();
        assert AlarmTimes.next(90,0,fold,ny,"")==Instant.parse("2025-11-02T06:30:00Z").toEpochMilli();
        assert DiaryMath.efficiency(fri, fri+480*60000L, fri+490*60000L,15d,null)==null;
        assert DiaryMath.duration(fri, fri+480*60000L,15d,5d)==460d;
        System.out.println("OK: recorrência, salto, soneca, fuso e medidas do diário");
    }
}

