package pl.fp.wydatki.analysis;

import pl.fp.wydatki.model.Prediction;
import pl.fp.wydatki.model.TrendPoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;

/**
 * FP: predykcja jako dwie niezależne czyste funkcje.
 *
 * predictLinear – regresja liniowa MKW (Metoda Najmniejszych Kwadratów).
 *   Wejście: lista TrendPoint (tylko agregaty, nie surowe Expense).
 *   Wyjście: List<Prediction> z przewidywaną kwotą i R² jako miarą pewności.
 *
 * predictEMA – Exponential Moving Average (ważona, nowsze dane mają większy wpływ).
 *   Pewność mierzona przez 1 - CV (współczynnik zmienności).
 *
 * Obie funkcje: determinizm + brak efektów ubocznych gwarantowane przez brak stanu.
 * Pomocnicze (computeR2, coefficientOfVariation) są package-private pod testy.
 */
public final class ExpensePredictor {
    private ExpensePredictor() {}

    public static List<Prediction> predictLinear(List<TrendPoint> monthlyTrend, int months) {
        double[] y = toDoubleArray(monthlyTrend);
        int n      = y.length;
        double[] x = IntStream.range(0, n).asDoubleStream().toArray();

        double xMean = mean(x);
        double yMean = mean(y);

        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            num += (x[i] - xMean) * (y[i] - yMean);
            den += (x[i] - xMean) * (x[i] - xMean);
        }
        double slope     = den == 0 ? 0 : num / den;
        double intercept = yMean - slope * xMean;
        double r2        = computeR2(y, x, slope, intercept);

        YearMonth lastMonth = YearMonth.parse(monthlyTrend.get(n - 1).periodLabel());

        // FP: IntStream.rangeClosed jako funkcyjny generator sekwencji miesięcy
        return IntStream.rangeClosed(1, months)
                .mapToObj(i -> {
                    double raw  = intercept + slope * (n - 1 + i);
                    BigDecimal predicted = BigDecimal.valueOf(Math.max(0, raw))
                            .setScale(2, RoundingMode.HALF_UP);
                    return new Prediction(lastMonth.plusMonths(i), predicted, Math.max(0, r2));
                })
                .toList();
    }

    // EMA: alpha = 2/(n+1), każda nowa wartość ma większą wagę niż poprzednia
    public static List<Prediction> predictEMA(List<TrendPoint> monthlyTrend, int months) {
        double[] y    = toDoubleArray(monthlyTrend);
        int n         = y.length;
        double alpha  = 2.0 / (n + 1);
        double ema    = y[0];
        for (double v : y) ema = alpha * v + (1 - alpha) * ema;

        double confidence = Math.max(0, 1 - coefficientOfVariation(y));
        YearMonth lastMonth = YearMonth.parse(monthlyTrend.get(n - 1).periodLabel());
        final double finalEma = ema;

        return IntStream.rangeClosed(1, months)
                .mapToObj(i -> new Prediction(
                        lastMonth.plusMonths(i),
                        BigDecimal.valueOf(finalEma).setScale(2, RoundingMode.HALF_UP),
                        confidence
                ))
                .toList();
    }

    // ── Czyste funkcje statystyczne (package-private dla testów) ──────────

    private static double[] toDoubleArray(List<TrendPoint> trend) {
        return trend.stream().mapToDouble(tp -> tp.total().doubleValue()).toArray();
    }

    private static double mean(double[] v) {
        double s = 0;
        for (double d : v) s += d;
        return s / v.length;
    }

    static double computeR2(double[] y, double[] x, double slope, double intercept) {
        double yMean = mean(y);
        double ssTot = 0, ssRes = 0;
        for (int i = 0; i < y.length; i++) {
            ssTot += (y[i] - yMean) * (y[i] - yMean);
            ssRes += (y[i] - (intercept + slope * x[i])) * (y[i] - (intercept + slope * x[i]));
        }
        return ssTot == 0 ? 0 : 1 - ssRes / ssTot;
    }

    static double coefficientOfVariation(double[] y) {
        double m  = mean(y);
        if (m == 0) return 1;
        double variance = 0;
        for (double v : y) variance += (v - m) * (v - m);
        return Math.sqrt(variance / y.length) / m;
    }
}
