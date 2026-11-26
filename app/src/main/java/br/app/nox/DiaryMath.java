package br.app.nox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Independent of Android so duration and circular-clock math can be tested on a JVM. */
final class DiaryMath {
    private DiaryMath() {}

    static Double duration(long bed, long wake, Double latency, Double waso) {
        if (bed <= 0 || wake < bed || latency == null || latency < 0 || (waso != null && waso < 0)) return null;
        double value = (wake - bed) / 60000d - latency - (waso == null ? 0d : waso);
        return value < 0 ? null : value;
    }

    static Double efficiency(long bed, long wake, long up, Double reported, Double waso) {
        if (waso == null || up <= bed || up < wake) return null;
        Double sleep = duration(bed, wake, reported, waso);
        return sleep == null ? null : 100d * sleep / ((up - bed) / 60000d);
    }

    static Double mean(List<Double> values) {
        if (values.isEmpty()) return null;
        double sum = 0;
        for (double value : values) sum += value;
        return sum / values.size();
    }

    static Double median(List<Double> values) {
        if (values.isEmpty()) return null;
        ArrayList<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int m = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(m - 1) + sorted.get(m)) / 2 : sorted.get(m);
    }

    /** RMS distance from circular mean, minutes. Defined only with at least two interpretable observations. */
    static Double clockSpread(List<Double> minutes) {
        if (minutes.size() < 2) return null;
        double x = 0, y = 0;
        for (double t : minutes) {
            double a = t * Math.PI / 720;
            x += Math.cos(a);
            y += Math.sin(a);
        }
        if (Math.hypot(x, y) < 0.000001 * minutes.size()) return null;
        double center = Math.atan2(y, x) * 720 / Math.PI;
        double squares = 0;
        for (double t : minutes) {
            double d = ((t - center + 2160) % 1440) - 720;
            squares += d * d;
        }
        return Math.sqrt(squares / minutes.size());
    }
}
