package pl.fp.wydatki.processing;

import pl.fp.wydatki.model.Category;
import pl.fp.wydatki.model.CategorySummary;
import pl.fp.wydatki.model.Expense;
import pl.fp.wydatki.model.Period;
import pl.fp.wydatki.model.TrendPoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FP: agregacja wyłącznie przez Collectors i Stream API – zero jawnych mutacji.
 *
 * groupingBy + collectingAndThen = funkcjonalny odpowiednik GROUP BY + SELECT
 * bez żadnej zmiennej pośredniej. Wszystkie metody są statyczne i nie mają stanu.
 */
public final class ExpenseAggregator {
    private ExpenseAggregator() {}

    public static List<CategorySummary> summarizeByCategory(List<Expense> expenses) {
        return expenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::category,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                ExpenseAggregator::toCategorySummary
                        )
                ))
                .values().stream()
                .sorted(Comparator.comparing(CategorySummary::total).reversed())
                .toList();
    }

    // FP: pomocnicza czysta funkcja – List<Expense> (tej samej kategorii) → CategorySummary
    private static CategorySummary toCategorySummary(List<Expense> group) {
        BigDecimal total = group.stream()
                .map(Expense::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = group.size();
        BigDecimal average = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return new CategorySummary(group.get(0).category(), total, count, average);
    }

    public static List<TrendPoint> trendByPeriod(List<Expense> expenses, Period period) {
        // FP: switch expression jako wyrażenie (nie instrukcja) – zwraca wartość bezpośrednio
        Function<Expense, String> keyFn = switch (period) {
            case DAY   -> e -> e.date().toString();
            case WEEK  -> e -> e.date().getYear() + "-W" +
                               String.format("%02d", e.date().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTH -> e -> DateTimeFormatter.ofPattern("yyyy-MM").format(e.date());
        };
        return aggregateByKey(expenses, keyFn);
    }

    private static List<TrendPoint> aggregateByKey(List<Expense> expenses,
                                                    Function<Expense, String> keyFn) {
        return expenses.stream()
                .collect(Collectors.groupingBy(keyFn))
                .entrySet().stream()
                .map(entry -> {
                    BigDecimal total = entry.getValue().stream()
                            .map(Expense::amount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new TrendPoint(entry.getKey(), total, entry.getValue().size());
                })
                .sorted(Comparator.comparing(TrendPoint::periodLabel))
                .toList();
    }

    public static BigDecimal totalAmount(List<Expense> expenses) {
        return expenses.stream()
                .map(Expense::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
