package pl.fp.wydatki;

import pl.fp.wydatki.analysis.AnomalyDetector;
import pl.fp.wydatki.analysis.ExpensePredictor;
import pl.fp.wydatki.analysis.TrendAnalyzer;
import pl.fp.wydatki.io.CsvReader;
import pl.fp.wydatki.io.JsonReader;
import pl.fp.wydatki.io.ResultExporter;
import pl.fp.wydatki.model.*;
import pl.fp.wydatki.processing.ExpenseAggregator;
import pl.fp.wydatki.processing.ExpenseFilter;
import pl.fp.wydatki.processing.Filters;
import pl.fp.wydatki.visualization.HtmlChartExporter;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Punkt wejścia – jedyne miejsce, gdzie I/O i czysta logika spotykają się.
 *
 * Struktura: wczytaj dane (I/O) → przetwarzaj (czyste funkcje) → zapisz wynik (I/O).
 * Żadna z czystych funkcji nie jest wywoływana z wewnątrz innej I/O.
 *
 * Użycie:
 *   mvn package
 *   java -jar target/analizator-wydatkow-1.0-SNAPSHOT-fat.jar          (domyślnie CSV)
 *   java -jar target/analizator-wydatkow-1.0-SNAPSHOT-fat.jar expenses.json
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║     Analizator Wydatków – FP 2024    ║");
        System.out.println("╚══════════════════════════════════════╝\n");

        // ── 1. I/O: wczytaj dane ──────────────────────────────────────────
        String source = args.length > 0 ? args[0] : "expenses.csv";
        List<Expense> expenses = loadFromClasspath(source);
        System.out.printf("Wczytano %d wydatków z: %s%n%n", expenses.size(), source);

        // ── 2. Czyste funkcje: agregacja ──────────────────────────────────
        List<CategorySummary> summaries = ExpenseAggregator.summarizeByCategory(expenses);
        System.out.println("── Wydatki wg kategorii ───────────────────────────────────");
        summaries.forEach(s -> System.out.printf(
                "  %-12s  suma: %8.2f PLN   n: %3d   śr: %7.2f PLN%n",
                s.category(), s.total(), s.count(), s.average()));
        System.out.printf("  %s%n  ŁĄCZNIE:      %8.2f PLN%n",
                "─".repeat(58), ExpenseAggregator.totalAmount(expenses));

        // ── 3. Trendy ─────────────────────────────────────────────────────
        List<TrendPoint> monthly  = TrendAnalyzer.monthlyTrend(expenses);
        List<TrendPoint> movingAvg = TrendAnalyzer.movingAverage(monthly, 3);
        System.out.println("\n── Trend miesięczny ────────────────────────────────────────");
        monthly.forEach(t -> System.out.printf("  %s:  %8.2f PLN  (n=%d)%n",
                t.periodLabel(), t.total(), t.count()));

        // ── 4. Anomalie ───────────────────────────────────────────────────
        List<Anomaly> anomalies = AnomalyDetector.detectByIQR(expenses);
        System.out.printf("%n── Anomalie IQR – znaleziono: %d ─────────────────────────%n",
                anomalies.size());
        anomalies.forEach(a -> System.out.printf(
                "  %s  %-12s  %8.2f PLN  +%.1f%% powyżej średniej  %s%n",
                a.expense().date(), a.expense().category(),
                a.expense().amount(), a.deviation(), a.expense().description()));

        // ── 5. Filtrowanie (demonstracja kompozycji predykatów) ───────────
        List<Expense> expensive = ExpenseFilter.filter(expenses,
                Filters.byMinAmount(new BigDecimal("300.00"))
                        .and(Filters.byCategory(Category.ZAKUPY)));
        System.out.printf("%n── Zakupy ≥ 300 PLN: %d transakcji ────────────────────────%n",
                expensive.size());
        expensive.stream().limit(5).forEach(e -> System.out.printf(
                "  %s  %8.2f PLN  %s%n", e.date(), e.amount(), e.description()));

        // ── 6. Predykcja ──────────────────────────────────────────────────
        List<Prediction> predictions = ExpensePredictor.predictLinear(monthly, 3);
        System.out.println("\n── Predykcja – kolejne 3 miesiące (regresja liniowa) ───────");
        predictions.forEach(p -> System.out.printf(
                "  %s:  %8.2f PLN   R²=%.3f%n",
                p.month(), p.predictedAmount(), p.confidence()));

        // ── 7. I/O: eksport HTML ──────────────────────────────────────────
        String html    = HtmlChartExporter.export(summaries, monthly, movingAvg, anomalies, predictions);
        Path   outPath = Path.of("raport.html");
        try (Writer w  = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8)) {
            ResultExporter.writeHtml(w, html);
        }
        System.out.printf("%nRaport HTML zapisano: %s%n", outPath.toAbsolutePath());
        System.out.println("Otwórz plik w przeglądarce, aby zobaczyć wykresy.\n");
    }

    private static List<Expense> loadFromClasspath(String name) throws Exception {
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream(name)) {
            if (is == null) throw new FileNotFoundException("Brak zasobu: " + name);
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            return name.endsWith(".json") ? JsonReader.read(reader) : CsvReader.read(reader);
        }
    }
}
