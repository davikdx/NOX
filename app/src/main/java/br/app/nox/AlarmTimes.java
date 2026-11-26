package br.app.nox;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/** Civil-time alarms follow the device zone. A repeated hour rings once per date. */
final class AlarmTimes {
    static final long MINUTE = 60000L;

    static long onDate(LocalDate date, int minute, ZoneId zone, long after) {
        if (minute < 0 || minute >= 1440) throw new IllegalArgumentException("Horário inválido");
        LocalDateTime time = date.atTime(LocalTime.of(minute / 60, minute % 60));
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(time);
        if (offsets.isEmpty()) {
            long shifted = time.atZone(zone).toInstant().toEpochMilli();
            return shifted > after ? shifted : 0;
        }
        for (ZoneOffset offset : offsets) {
            long result = time.toInstant(offset).toEpochMilli();
            if (result > after) return result;
        }
        return 0;
    }

    static long next(int minute, int mask, long after, ZoneId zone, String omitDate) {
        if (mask < 0 || mask > 127) throw new IllegalArgumentException("Dias inválidos");
        LocalDate first = Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
        for (int i = 0; i <= 14; i++) {
            LocalDate date = first.plusDays(i);
            if (date.toString().equals(omitDate)) continue;
            int day = 1 << (date.getDayOfWeek().getValue() - 1);
            if (mask != 0 && (mask & day) == 0) continue;
            long time = onDate(date, minute, zone, after);
            if (time > 0) return time;
        }
        throw new IllegalStateException("Não foi possível calcular a próxima ocorrência");
    }

    static String date(long time) {
        return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    static long snooze(long now, int minutes, long deadline, int count, int max) {
        if (minutes < 1 || (max > 0 && count >= max) || (deadline > 0 && now >= deadline)) return 0;
        long at = now + Math.min(180, minutes) * MINUTE;
        return deadline > 0 ? Math.min(deadline, at) : at;
    }
}
