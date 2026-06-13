package pl.fp.wydatki.analysis;

import org.junit.jupiter.api.Test;
import pl.fp.wydatki.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy weryfikują:
 *  - poprawność obu metod wykrywania (IQR i StdDev),
 *  - że metody nie zwracają fałszywych pozytywów na danych równomiernych,
 *  - że wyniki są spójne z typem AnomalyType (sealed),
 *  - determinizm funkcji statystycznych (mean, stdDev, percentile).
 */
class AnomalyDetectorTest {

    // 5 normalnych + 1 skrajna anomalia (500 vs. zakres 40-60)
    private static final List<Expense> WITH_ANOMALY = List.of(
            new Expense(1, LocalDate.of(2024, 1,  1), new BigDecimal("40.00"),  Category.JEDZENIE, "A"),
            new Expense(2, LocalDate.of(2024, 1,  8), new BigDecimal("45.00"),  Category.JEDZENIE, "B"),
            new Expense(3, LocalDate.of(2024, 1, 15), new BigDecimal("50.00"),  Category.JEDZENIE, "C"),
            new Expense(4, LocalDate.of(2024, 1, 22), new BigDecimal("55.00"),  Category.JEDZENIE, "D"),
            new Expense(5, LocalDate.of(2024, 1, 29), new BigDecimal("60.00"),  Category.JEDZENIE, "E"),
            new Expense(6, LocalDate.of(2024, 2,  5), new BigDecimal("500.00"), Category.JEDZENIE, "ANOMALIA")
    );

    // Dane bez anomalii – wartości bliskie sobie
    private static final List<Expense> UNIFORM = List.of(
            new Expense(1, LocalDate.of(2024, 1, 1), new BigDecimal("49.00"), Category.JEDZENIE, "A"),
            new Expense(2, LocalDate.of(2024, 1, 2), new BigDecimal("51.00"), Category.JEDZENIE, "B"),
            new Expense(3, LocalDate.of(2024, 1, 3), new BigDecimal("50.00"), Category.JEDZENIE, "C"),
            new Expense(4, LocalDate.of(2024, 1, 4), new BigDecimal("52.00"), Category.JEDZENIE, "D"),
            new Expense(5, LocalDate.of(2024, 1, 5), new BigDecimal("48.00"), Category.JEDZENIE, "E")
    );

    @Test
    void detectByIQR_findsOneAnomaly() {
        assertEquals(1, AnomalyDetector.detectByIQR(WITH_ANOMALY).size());
    }

    @Test
    void detectByIQR_findsCorrectExpense() {
        Anomaly a = AnomalyDetector.detectByIQR(WITH_ANOMALY).get(0);
        assertEquals(6, a.expense().id());
        assertEquals(new BigDecimal("500.00"), a.expense().amount());
    }

    @Test
    void detectByStdDev_findsAnomaly() {
        assertEquals(1, AnomalyDetector.detectByStdDev(WITH_ANOMALY).size());
    }

    @Test
    void detectByIQR_noFalsePositivesOnUniformData() {
        assertTrue(AnomalyDetector.detectByIQR(UNIFORM).isEmpty(),
                "Dane równomierne nie powinny generować anomalii");
    }

    @Test
    void detectByStdDev_noFalsePositivesOnUniformData() {
        assertTrue(AnomalyDetector.detectByStdDev(UNIFORM).isEmpty());
    }

    @Test
    void detectByIQR_anomalyTypeIsIQRInstance() {
        Anomaly a = AnomalyDetector.detectByIQR(WITH_ANOMALY).get(0);
        assertInstanceOf(AnomalyType.IQR.class, a.type(),
                "detectByIQR musi zwracać AnomalyType.IQR");
    }

    @Test
    void detectByStdDev_anomalyTypeIsStdDevInstance() {
        Anomaly a = AnomalyDetector.detectByStdDev(WITH_ANOMALY).get(0);
        assertInstanceOf(AnomalyType.StdDev.class, a.type(),
                "detectByStdDev musi zwracać AnomalyType.StdDev");
    }

    @Test
    void anomaly_deviationIsPositive() {
        AnomalyDetector.detectByIQR(WITH_ANOMALY)
                .forEach(a -> assertTrue(a.deviation() > 0,
                        "Odchylenie anomalii musi być dodatnie"));
    }

    @Test
    void detectByIQR_isDeterministic() {
        assertEquals(
                AnomalyDetector.detectByIQR(WITH_ANOMALY),
                AnomalyDetector.detectByIQR(WITH_ANOMALY));
    }

    // ── Testy funkcji statystycznych ──────────────────────────────────────

    @Test
    void mean_correctForSymmetricData() {
        assertEquals(30.0, AnomalyDetector.mean(new double[]{10, 20, 30, 40, 50}), 1e-9);
    }

    @Test
    void stdDev_knownValue() {
        // Klasyczny przykład: {2,4,4,4,5,5,7,9} → stdDev = 2.0
        double[] v = {2, 4, 4, 4, 5, 5, 7, 9};
        assertEquals(2.0, AnomalyDetector.stdDev(v, AnomalyDetector.mean(v)), 1e-9);
    }

    @Test
    void percentile_q1OfFourElements() {
        // sorted: [1,2,3,4], Q1 = index 0.75 → 1 + 0.75*(2-1) = 1.75
        double[] sorted = {1, 2, 3, 4};
        assertEquals(1.75, AnomalyDetector.percentile(sorted, 25), 1e-9);
    }

    @Test
    void percentile_medianOfOddArray() {
        double[] sorted = {1, 2, 3, 4, 5};
        assertEquals(3.0, AnomalyDetector.percentile(sorted, 50), 1e-9);
    }

    @Test
    void anomalyType_labelIsNonEmpty() {
        // Sealed interface – każdy wariant musi implementować label()
        AnomalyType stdDev = new AnomalyType.StdDev(2.0, 150.0);
        AnomalyType iqr    = new AnomalyType.IQR(1.5, 120.0);
        assertFalse(stdDev.label().isEmpty());
        assertFalse(iqr.label().isEmpty());
    }
}
