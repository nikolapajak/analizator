package pl.fp.wydatki.analysis;

import org.junit.jupiter.api.Test;
import pl.fp.wydatki.model.Prediction;
import pl.fp.wydatki.model.TrendPoint;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy predykcji – weryfikują matematyczną poprawność i właściwości FP.
 *
 * Dane testowe: trend liniowy 100→200→300→400.
 * Dla takiego trendu regresja liniowa zwraca slope=100, intercept=100,
 * więc predict(x=4) = 500.00 PLN, R² = 1.0 (idealne dopasowanie).
 */
class ExpensePredictorTest {

    private static final List<TrendPoint> LINEAR = List.of(
            new TrendPoint("2024-01", new BigDecimal("100.00"), 5L),
            new TrendPoint("2024-02", new BigDecimal("200.00"), 6L),
            new TrendPoint("2024-03", new BigDecimal("300.00"), 5L),
            new TrendPoint("2024-04", new BigDecimal("400.00"), 6L)
    );

    @Test
    void predictLinear_returnsRequestedCount() {
        assertEquals(3, ExpensePredictor.predictLinear(LINEAR, 3).size());
    }

    @Test
    void predictLinear_firstPredictionMonthIsCorrect() {
        Prediction p = ExpensePredictor.predictLinear(LINEAR, 1).get(0);
        assertEquals(YearMonth.of(2024, 5), p.month(),
                "Pierwsza predykcja musi być bezpośrednio po ostatnim punkcie danych");
    }

    @Test
    void predictLinear_exactValueForPerfectLinearTrend() {
        // slope=100, intercept=100 → predict(x=4)=500
        Prediction p = ExpensePredictor.predictLinear(LINEAR, 1).get(0);
        assertEquals(new BigDecimal("500.00"), p.predictedAmount());
    }

    @Test
    void predictLinear_r2EqualsOneForPerfectFit() {
        Prediction p = ExpensePredictor.predictLinear(LINEAR, 1).get(0);
        assertEquals(1.0, p.confidence(), 1e-9,
                "Idealna linia prosta musi dać R²=1.0");
    }

    @Test
    void predictLinear_consecutiveMonthsAreOrdered() {
        List<Prediction> result = ExpensePredictor.predictLinear(LINEAR, 3);
        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i - 1).month().isBefore(result.get(i).month()),
                    "Miesiące predykcji muszą być w kolejności rosnącej");
        }
    }

    @Test
    void predictLinear_predictedAmountNeverNegative() {
        // Trend silnie malejący → ekstrapolacja może dać wartość ujemną → powinno być 0
        List<TrendPoint> declining = List.of(
                new TrendPoint("2024-01", new BigDecimal("100.00"), 3L),
                new TrendPoint("2024-02", new BigDecimal("50.00"),  3L),
                new TrendPoint("2024-03", new BigDecimal("10.00"),  3L)
        );
        ExpensePredictor.predictLinear(declining, 6)
                .forEach(p -> assertTrue(
                        p.predictedAmount().compareTo(BigDecimal.ZERO) >= 0,
                        "Kwota wydatku nie może być ujemna"));
    }

    @Test
    void predictEMA_returnsRequestedCount() {
        assertEquals(3, ExpensePredictor.predictEMA(LINEAR, 3).size());
    }

    @Test
    void predictEMA_allPredictionsHaveSameValue() {
        // EMA daje stałą projekcję (brak trendu w predykcji)
        List<Prediction> result = ExpensePredictor.predictEMA(LINEAR, 3);
        BigDecimal first = result.get(0).predictedAmount();
        result.forEach(p -> assertEquals(first, p.predictedAmount()));
    }

    @Test
    void predictLinear_isDeterministic() {
        assertEquals(
                ExpensePredictor.predictLinear(LINEAR, 3),
                ExpensePredictor.predictLinear(LINEAR, 3),
                "Czysta funkcja musi być deterministyczna");
    }

    @Test
    void computeR2_perfectFitEqualsOne() {
        double[] y = {1, 2, 3, 4, 5};
        double[] x = {0, 1, 2, 3, 4};
        // y = 1*x + 1 → idealne dopasowanie
        assertEquals(1.0, ExpensePredictor.computeR2(y, x, 1.0, 1.0), 1e-9);
    }

    @Test
    void computeR2_horizontalLineWithVarianceGivesLowR2() {
        double[] y = {1, 3, 5, 7, 9};  // rosnące dane
        double[] x = {0, 1, 2, 3, 4};
        // Linia pozioma (slope=0, intercept=5) – słabe dopasowanie
        double r2 = ExpensePredictor.computeR2(y, x, 0.0, 5.0);
        assertEquals(0.0, r2, 1e-9);
    }

    @Test
    void coefficientOfVariation_lowForUniformData() {
        // Dane bliskie stałej → CV bliskie 0 → wysoka pewność predykcji
        double cv = ExpensePredictor.coefficientOfVariation(new double[]{99, 100, 101, 100, 99});
        assertTrue(cv < 0.02, "CV dla danych jednorodnych powinien być < 2%");
    }
}
