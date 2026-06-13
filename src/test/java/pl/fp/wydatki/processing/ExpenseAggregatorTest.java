package pl.fp.wydatki.processing;

import org.junit.jupiter.api.Test;
import pl.fp.wydatki.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy agregacji – weryfikują poprawność Collectors bez efektów ubocznych.
 */
class ExpenseAggregatorTest {

    private static final List<Expense> SAMPLE = List.of(
            new Expense(1, LocalDate.of(2024, 1,  5), new BigDecimal("100.00"), Category.JEDZENIE,  "A"),
            new Expense(2, LocalDate.of(2024, 1, 10), new BigDecimal("60.00"),  Category.JEDZENIE,  "B"),
            new Expense(3, LocalDate.of(2024, 2,  3), new BigDecimal("200.00"), Category.TRANSPORT, "C"),
            new Expense(4, LocalDate.of(2024, 2, 25), new BigDecimal("150.00"), Category.TRANSPORT, "D"),
            new Expense(5, LocalDate.of(2024, 3,  1), new BigDecimal("300.00"), Category.ZAKUPY,    "E")
    );

    @Test
    void summarizeByCategory_correctTotal() {
        List<CategorySummary> result = ExpenseAggregator.summarizeByCategory(SAMPLE);
        CategorySummary food = result.stream()
                .filter(s -> s.category() == Category.JEDZENIE)
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("160.00"), food.total());
    }

    @Test
    void summarizeByCategory_correctCount() {
        List<CategorySummary> result = ExpenseAggregator.summarizeByCategory(SAMPLE);
        CategorySummary food = result.stream()
                .filter(s -> s.category() == Category.JEDZENIE)
                .findFirst().orElseThrow();
        assertEquals(2L, food.count());
    }

    @Test
    void summarizeByCategory_correctAverage() {
        List<CategorySummary> result = ExpenseAggregator.summarizeByCategory(SAMPLE);
        CategorySummary food = result.stream()
                .filter(s -> s.category() == Category.JEDZENIE)
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("80.00"), food.average());
    }

    @Test
    void summarizeByCategory_sortedByTotalDescending() {
        List<CategorySummary> result = ExpenseAggregator.summarizeByCategory(SAMPLE);
        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i - 1).total().compareTo(result.get(i).total()) >= 0,
                    "Wynik powinien być posortowany malejąco po sumie");
        }
    }

    @Test
    void trendByPeriod_month_correctNumberOfPeriods() {
        List<TrendPoint> trend = ExpenseAggregator.trendByPeriod(SAMPLE, Period.MONTH);
        assertEquals(3, trend.size());
    }

    @Test
    void trendByPeriod_month_correctLabels() {
        List<TrendPoint> trend = ExpenseAggregator.trendByPeriod(SAMPLE, Period.MONTH);
        assertEquals("2024-01", trend.get(0).periodLabel());
        assertEquals("2024-02", trend.get(1).periodLabel());
        assertEquals("2024-03", trend.get(2).periodLabel());
    }

    @Test
    void trendByPeriod_month_correctJanuaryTotal() {
        List<TrendPoint> trend = ExpenseAggregator.trendByPeriod(SAMPLE, Period.MONTH);
        assertEquals(new BigDecimal("160.00"), trend.get(0).total());
    }

    @Test
    void trendByPeriod_sortedChronologically() {
        List<TrendPoint> trend = ExpenseAggregator.trendByPeriod(SAMPLE, Period.MONTH);
        for (int i = 1; i < trend.size(); i++) {
            assertTrue(trend.get(i - 1).periodLabel().compareTo(trend.get(i).periodLabel()) < 0,
                    "TrendPoint powinny być posortowane chronologicznie");
        }
    }

    @Test
    void totalAmount_sumIsCorrect() {
        assertEquals(new BigDecimal("810.00"), ExpenseAggregator.totalAmount(SAMPLE));
    }

    @Test
    void totalAmount_emptyListReturnsZero() {
        assertEquals(BigDecimal.ZERO, ExpenseAggregator.totalAmount(List.of()));
    }

    @Test
    void summarizeByCategory_isDeterministic() {
        assertEquals(
                ExpenseAggregator.summarizeByCategory(SAMPLE),
                ExpenseAggregator.summarizeByCategory(SAMPLE),
                "Czysta funkcja musi być deterministyczna");
    }
}
