package pl.fp.wydatki.analysis;

import pl.fp.wydatki.model.Expense;
import pl.fp.wydatki.model.Period;
import pl.fp.wydatki.model.TrendPoint;
import pl.fp.wydatki.processing.ExpenseAggregator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.IntStream;

/**
 * FP: analiza trendów przez transformacje List<TrendPoint> → List<TrendPoint>.
 *
 * movingAverage i growthRates są czystymi funkcjami na już zagregowanych danych –
 * dzięki temu są testowalnie niezależne od surowych Expense i od siebie nawzajem.
 * IntStream.range pozwala iterować z indeksem bez zmiennych pośrednich.
 */
public final class TrendAnalyzer {
    private TrendAnalyzer() {}

    public static List<TrendPoint> monthlyTrend(List<Expense> expenses) {
        return ExpenseAggregator.trendByPeriod(expenses, Period.MONTH);
    }

    public static List<TrendPoint> weeklyTrend(List<Expense> expenses) {
        return ExpenseAggregator.trendByPeriod(expenses, Period.WEEK);
    }

    // FP: IntStream.range jako funkcyjny indeksowany map – brak zmiennej pętli
    public static List<TrendPoint> movingAverage(List<TrendPoint> trend, int windowSize) {
        return IntStream.range(0, trend.size())
                .mapToObj(i -> {
                    int from = Math.max(0, i - windowSize + 1);
                    List<TrendPoint> window = trend.subList(from, i + 1);
                    BigDecimal sum = window.stream()
                            .map(TrendPoint::total)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal avg = sum.divide(
                            BigDecimal.valueOf(window.size()), 2, RoundingMode.HALF_UP);
                    return new TrendPoint(trend.get(i).periodLabel(), avg, trend.get(i).count());
                })
                .toList();
    }

    public static List<String> growthRates(List<TrendPoint> trend) {
        return IntStream.range(1, trend.size())
                .mapToObj(i -> {
                    BigDecimal prev = trend.get(i - 1).total();
                    BigDecimal curr = trend.get(i).total();
                    if (prev.compareTo(BigDecimal.ZERO) == 0)
                        return trend.get(i).periodLabel() + ": N/A";
                    BigDecimal change = curr.subtract(prev)
                            .divide(prev, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                    return trend.get(i).periodLabel() + ": " + change + "%";
                })
                .toList();
    }
}
