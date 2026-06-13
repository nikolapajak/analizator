package pl.fp.wydatki.analysis;

import pl.fp.wydatki.model.Anomaly;
import pl.fp.wydatki.model.AnomalyType;
import pl.fp.wydatki.model.Expense;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FP: wykrywanie anomalii jako dwie niezależne czyste funkcje.
 *
 * List<Expense> → List<Anomaly> – żadnych efektów ubocznych.
 * Wspólny szkielet detectPerCategory przyjmuje strategię wykrywania
 * jako Function<List<Expense>, List<Anomaly>> – wzorzec Strategy przez wyższy rząd funkcji,
 * bez dziedziczenia ani interfejsów.
 *
 * Pomocnicze funkcje statystyczne (mean, stdDev, percentile) są package-private
 * specjalnie pod testy – potwierdzają brak efektów ubocznych i determinizm.
 */
public final class AnomalyDetector {
    private AnomalyDetector() {}

    private static final double SIGMA_MULTIPLIER = 2.0;
    private static final double IQR_MULTIPLIER   = 1.5;

    public static List<Anomaly> detectByStdDev(List<Expense> expenses) {
        return detectPerCategory(expenses, AnomalyDetector::stdDevAnomalies);
    }

    public static List<Anomaly> detectByIQR(List<Expense> expenses) {
        return detectPerCategory(expenses, AnomalyDetector::iqrAnomalies);
    }

    // FP: wyższy rząd – strategia wykrywania przekazana jako Function
    private static List<Anomaly> detectPerCategory(
            List<Expense> expenses,
            Function<List<Expense>, List<Anomaly>> detector) {

        return expenses.stream()
                .collect(Collectors.groupingBy(Expense::category))
                .values().stream()
                .flatMap(group -> detector.apply(group).stream())
                .sorted(Comparator.comparing(a -> a.expense().date()))
                .toList();
    }

    private static List<Anomaly> stdDevAnomalies(List<Expense> group) {
        double[] amounts  = toDoubleArray(group);
        double mean       = mean(amounts);
        double stdDev     = stdDev(amounts, mean);
        double threshold  = mean + SIGMA_MULTIPLIER * stdDev;

        return group.stream()
                .filter(e -> e.amount().doubleValue() > threshold)
                .map(e -> new Anomaly(
                        e,
                        new AnomalyType.StdDev(SIGMA_MULTIPLIER, threshold),
                        mean,
                        (e.amount().doubleValue() - mean) / mean * 100
                ))
                .toList();
    }

    private static List<Anomaly> iqrAnomalies(List<Expense> group) {
        double[] sorted = toDoubleArray(group);
        Arrays.sort(sorted);

        double q1    = percentile(sorted, 25);
        double q3    = percentile(sorted, 75);
        double fence = q3 + IQR_MULTIPLIER * (q3 - q1);
        double mean  = mean(sorted);

        return group.stream()
                .filter(e -> e.amount().doubleValue() > fence)
                .map(e -> new Anomaly(
                        e,
                        new AnomalyType.IQR(IQR_MULTIPLIER, fence),
                        mean,
                        (e.amount().doubleValue() - mean) / mean * 100
                ))
                .toList();
    }

    // ── Czyste funkcje statystyczne ────────────────────────────────────────

    private static double[] toDoubleArray(List<Expense> expenses) {
        return expenses.stream()
                .mapToDouble(e -> e.amount().doubleValue())
                .toArray();
    }

    static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    static double stdDev(double[] values, double mean) {
        double variance = 0;
        for (double v : values) variance += (v - mean) * (v - mean);
        return Math.sqrt(variance / values.length);
    }

    // Interpolacja liniowa między elementami posortowanej tablicy
    static double percentile(double[] sorted, double p) {
        double index  = (p / 100.0) * (sorted.length - 1);
        int lower     = (int) index;
        int upper     = Math.min(lower + 1, sorted.length - 1);
        double frac   = index - lower;
        return sorted[lower] + frac * (sorted[upper] - sorted[lower]);
    }
}
