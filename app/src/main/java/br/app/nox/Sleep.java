package br.app.nox;

import java.time.*;
import java.time.zone.ZoneRules;
import java.util.*;

/** Independent domain rules. Times are instants in milliseconds. Durations are minutes. */
public final class Sleep {
    public static final long MIN = 60_000L;
    public static final int MIN_SAMPLE = 7;
    private Sleep() {}

    public static long nextTime(long after, int minute, ZoneId zone) {
        if (minute < 0 || minute >= 1440) throw new IllegalArgumentException("Horário inválido.");
        LocalDate date = Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
        LocalTime time = LocalTime.of(minute / 60, minute % 60);
        ZoneRules rules = zone.getRules();
        for (int day = 0; day < 3; day++) {
            LocalDateTime wall = LocalDateTime.of(date.plusDays(day), time);
            List<ZoneOffset> offsets = rules.getValidOffsets(wall);
            // A nonexistent civil time becomes the first valid instant following the gap.
            if (offsets.isEmpty()) {
                long boundary = rules.getTransition(wall).getInstant().toEpochMilli();
                if (boundary > after) return boundary;
            } else {
                for (ZoneOffset offset : offsets) {
                    long epoch = wall.toInstant(offset).toEpochMilli();
                    if (epoch > after) return epoch;
                }
            }
        }
        throw new IllegalArgumentException("Não foi possível resolver o horário local.");
    }

    public static int circular(int minute, int reference) {
        return Math.floorMod(minute - reference + 720, 1440) - 720;
    }

    public static int wall(long epoch, ZoneId zone) {
        LocalTime t = Instant.ofEpochMilli(epoch).atZone(zone).toLocalTime();
        return 60 * t.getHour() + t.getMinute();
    }

    public static Integer median(List<Integer> list) {
        if (list.isEmpty()) return null;
        ArrayList<Integer> sorted = new ArrayList<>(list);
        Collections.sort(sorted);
        int n = sorted.size();
        return Math.floorDiv(sorted.get((n-1)/2) + sorted.get(n/2) + 1, 2);
    }

    public static final class Entry {
        public long wake;
        public Integer latency, quality, fatigue, sleepMinutes;
        public boolean weekend;
        public int minute;
    }

    public static final class Profile {
        public int latency, nLatency, nWake, nGood, reference;
        public Integer typical, good, spread, duration;
        public boolean weekendGroup;
    }

    public static Profile profile(List<Entry> source, int initial, int reference, long deadline,
            ZoneId zone, boolean enabled, Integer ownWake, Integer ownLatency) {
        Profile p = new Profile();
        p.latency = initial;
        p.reference = reference;
        if (!enabled) return p;
        LocalDate targetDate = Instant.ofEpochMilli(deadline).atZone(zone).toLocalDate();
        long cutoff = targetDate.minusDays(90).atStartOfDay(zone).toInstant().toEpochMilli();
        ArrayList<Entry> valid = new ArrayList<>();
        for (Entry e : source) {
            if (e.wake <= 0 || e.wake < cutoff || e.wake >= deadline) continue;
            valid.add(e);
        }
        valid.sort((a,b) -> Long.compare(b.wake,a.wake));
        boolean weekend = targetDate.getDayOfWeek().getValue() >= 6;
        ArrayList<Entry> likeDays = new ArrayList<>();
        for (Entry e : valid) if (e.weekend == weekend) likeDays.add(e);
        p.weekendGroup = likeDays.size() >= MIN_SAMPLE;
        List<Entry> group = p.weekendGroup ? likeDays : valid;
        List<Integer> lat = new ArrayList<>(), wakes = new ArrayList<>(), good = new ArrayList<>(), durations = new ArrayList<>();
        for (Entry e : group.subList(0, Math.min(group.size(), 30))) {
            if (e.latency != null && e.latency >= 0 && e.latency <= 360) lat.add(e.latency);
            if (e.sleepMinutes != null && e.sleepMinutes >= 0 && e.sleepMinutes <= 1440) durations.add(e.sleepMinutes);
            int off = circular(e.minute, reference);
            if (Math.abs(off) > 360) continue; // do not mix an opposite shift with this schedule
            wakes.add(off);
            if (e.quality != null && e.quality >= 4 && e.fatigue != null && e.fatigue <= 1) good.add(off);
        }
        p.nWake = wakes.size();
        p.nGood = good.size();
        p.nLatency = lat.size();
        if (lat.size() >= MIN_SAMPLE) p.latency = median(lat);
        if (ownLatency != null) p.latency = ownLatency;
        p.duration = median(durations);
        if (wakes.size() >= MIN_SAMPLE) {
            int mid = median(wakes);
            List<Integer> deviations = new ArrayList<>();
            for (int x : wakes) deviations.add(Math.abs(x-mid));
            p.spread = median(deviations);
            if (p.spread <= 30) p.typical = Math.floorMod(reference+mid,1440);
        }
        if (ownWake != null) p.typical = ownWake;
        if (good.size() >= MIN_SAMPLE) p.good = Math.floorMod(reference+median(good),1440);
        return p;
    }

    public static final class Option {
        public long at;
        public int minutes, early;
        public String why;
        public double cycles;
        Option(long bed, int latency, long deadline, long at, String why) {
            this.at = at;
            this.early = (int)((deadline - at) / MIN);
            this.minutes = (int)Math.max(0, (at-bed)/MIN-latency);
            this.cycles = this.minutes / 90.0;
            this.why = why;
        }
    }

    public static List<Option> options(long bed, long deadline, int goal, int window, Profile p, ZoneId zone) {
        if (deadline <= bed || goal < 1 || window < 0 || window > 60 || p.latency < 0)
            throw new IllegalArgumentException("Horários ou duração inválidos.");
        Option limit = new Option(bed,p.latency,deadline,deadline,"Preserva o maior tempo disponível até seu limite.");
        Option best = limit;
        int baseMinute = wall(deadline,zone);
        // Advance only toward a well-established routine and only with the full target preserved.
        if (p.typical != null && limit.minutes >= Math.max(goal,420)) {
            int desired = -circular(p.typical,baseMinute);
            if (desired >= 5 && window >= 5) {
                int early = Math.min(Math.min(15,window),desired);
                early = Math.min(early, limit.minutes - Math.max(goal,420));
                if (early >= 5) {
                    // For equally close civil-minute options, favorable diary entries can refine by one minute.
                    if (p.good != null && early < desired && early > 5 &&
                        Math.abs(circular(baseMinute-early+1,p.good)) < Math.abs(circular(baseMinute-early,p.good)))
                        early--;
                    best = new Option(bed,p.latency,deadline,deadline-early*MIN,
                         "Aproxima o despertar do seu horário habitual e preserva a meta e pelo menos 7 h previstas.");
                }
            }
        }
        LinkedHashMap<Long,Option> out = new LinkedHashMap<>();
        out.put(best.at,best);
        out.put(limit.at,limit);
        // Assisted mode offers alternatives. Never recommend a cycle-only advance automatically.
        if (window > 0 && limit.minutes > 0) {
            ArrayList<Option> candidates = new ArrayList<>();
            for (int early=1; early<=window; early++) {
                long instant=deadline-early*MIN;
                if (instant<=bed+p.latency*MIN) continue;
                candidates.add(new Option(bed,p.latency,deadline,instant,"Alternativa manual dentro da sua janela."));
            }
            // Prefer an option retaining the goal; cycle proximity only orders manual choices in that group.
            candidates.sort(Comparator.<Option>comparingInt(x -> x.minutes>=Math.max(goal,420) ? 0:1)
                .thenComparingDouble(x -> Math.abs(x.cycles-Math.rint(x.cycles)))
                .thenComparingInt(x -> x.early));
            if (!candidates.isEmpty()) {
                Option choice = candidates.get(0);
                if (!out.containsKey(choice.at)) out.put(choice.at,choice);
                Option edge = new Option(bed,p.latency,deadline,deadline-window*MIN,
                    "Extremo da janela definida por você.");
                if (edge.at>bed+p.latency*MIN && !out.containsKey(edge.at)) out.put(edge.at,edge);
            }
        }
        return new ArrayList<>(out.values());
    }
}
