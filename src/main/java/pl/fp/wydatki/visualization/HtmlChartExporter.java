package pl.fp.wydatki.visualization;

import pl.fp.wydatki.model.Anomaly;
import pl.fp.wydatki.model.CategorySummary;
import pl.fp.wydatki.model.Prediction;
import pl.fp.wydatki.model.TrendPoint;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FP: czysta funkcja (dane) → String (HTML).
 *
 * Brak efektów ubocznych – metoda export() nie dotyka systemu plików.
 * Zapis do pliku należy do io.ResultExporter.
 *
 * Każda sekcja HTML jest generowana przez prywatne czyste funkcje
 * operujące na danych wejściowych. Szablonowanie przez String.replace()
 * jest celowo proste – HTML generation to transformacja stringów,
 * nie logika biznesowa.
 */
public final class HtmlChartExporter {
    private HtmlChartExporter() {}

    public static String export(
            List<CategorySummary> summaries,
            List<TrendPoint>      monthlyTrend,
            List<TrendPoint>      movingAvg,
            List<Anomaly>         anomalies,
            List<Prediction>      predictions) {

        return TEMPLATE
                .replace("{{CATEGORY_LABELS}}", jsonLabels(summaries, s -> quote(s.category().name())))
                .replace("{{CATEGORY_DATA}}",   jsonValues(summaries, s -> s.total().toPlainString()))
                .replace("{{TREND_LABELS}}",    jsonLabels(monthlyTrend, t -> quote(t.periodLabel())))
                .replace("{{TREND_DATA}}",      jsonValues(monthlyTrend, t -> t.total().toPlainString()))
                .replace("{{MA_DATA}}",         jsonValues(movingAvg,    t -> t.total().toPlainString()))
                .replace("{{ANOMALY_ROWS}}",    anomalyRows(anomalies))
                .replace("{{PRED_LABELS}}",     jsonLabels(predictions, p -> quote(p.month().toString())))
                .replace("{{PRED_DATA}}",       jsonValues(predictions, p -> p.predictedAmount().toPlainString()));
    }

    // ── Pomocnicze czyste funkcje ──────────────────────────────────────────

    private static <T> String jsonLabels(List<T> list, Function<T, String> fn) {
        return list.stream().map(fn).collect(Collectors.joining(",", "[", "]"));
    }

    private static <T> String jsonValues(List<T> list, Function<T, String> fn) {
        return list.stream().map(fn).collect(Collectors.joining(",", "[", "]"));
    }

    private static String quote(String s) { return "\"" + s + "\""; }

    private static String anomalyRows(List<Anomaly> anomalies) {
        return anomalies.stream()
                .map(a -> "<tr>" +
                        td(a.expense().date().toString()) +
                        td(a.expense().category().name()) +
                        td(a.expense().description()) +
                        "<td class='amt'>" + a.expense().amount() + " PLN</td>" +
                        td(a.type().label()) +
                        "<td class='dev'>+" + String.format("%.1f", a.deviation()) + "%</td>" +
                        "</tr>")
                .collect(Collectors.joining("\n"));
    }

    private static String td(String content) {
        return "<td>" + content + "</td>";
    }

    // ── Szablon HTML z wbudowanym Chart.js (CDN) ──────────────────────────

    private static final String TEMPLATE = """
            <!DOCTYPE html>
            <html lang="pl">
            <head>
              <meta charset="UTF-8">
              <title>Analizator Wydatków – Raport 2024</title>
              <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.3/dist/chart.umd.min.js"></script>
              <style>
                * { box-sizing: border-box; }
                body { font-family: system-ui, sans-serif; margin: 2rem; background: #f0f2f5; color: #222; }
                h1   { color: #1a2744; border-bottom: 3px solid #3b82f6; padding-bottom: .5rem; }
                h2   { color: #1e40af; font-size: 1.05rem; margin: 0 0 1rem; }
                .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
                .card { background: #fff; border-radius: 12px; padding: 1.5rem;
                        box-shadow: 0 1px 6px rgba(0,0,0,.08); }
                .full { grid-column: 1/-1; }
                table { width: 100%; border-collapse: collapse; font-size: .88rem; }
                th  { background: #1e40af; color: #fff; padding: .55rem 1rem; text-align: left; }
                td  { padding: .45rem 1rem; border-bottom: 1px solid #e5e7eb; }
                tr:nth-child(even) td { background: #f8fafc; }
                .amt { font-weight: 700; color: #dc2626; }
                .dev { font-weight: 700; color: #d97706; }
              </style>
            </head>
            <body>
              <h1>Analizator Wydatków – Raport 2024</h1>
              <div class="grid">

                <div class="card">
                  <h2>Budżet wg kategorii</h2>
                  <canvas id="catChart"></canvas>
                </div>

                <div class="card">
                  <h2>Trend miesięczny + średnia krocząca (3M)</h2>
                  <canvas id="trendChart"></canvas>
                </div>

                <div class="card">
                  <h2>Predykcja – kolejne 3 miesiące (regresja liniowa)</h2>
                  <canvas id="predChart"></canvas>
                </div>

                <div class="card full">
                  <h2>Wykryte anomalie</h2>
                  <table>
                    <thead><tr>
                      <th>Data</th><th>Kategoria</th><th>Opis</th>
                      <th>Kwota</th><th>Metoda</th><th>Odchylenie</th>
                    </tr></thead>
                    <tbody>{{ANOMALY_ROWS}}</tbody>
                  </table>
                </div>

              </div>
              <script>
                const PALETTE = ['#3b82f6','#ef4444','#10b981','#f59e0b','#8b5cf6','#06b6d4'];

                new Chart(document.getElementById('catChart'), {
                  type: 'doughnut',
                  data: {
                    labels: {{CATEGORY_LABELS}},
                    datasets: [{ data: {{CATEGORY_DATA}}, backgroundColor: PALETTE }]
                  },
                  options: { plugins: { legend: { position: 'bottom' } } }
                });

                new Chart(document.getElementById('trendChart'), {
                  type: 'line',
                  data: {
                    labels: {{TREND_LABELS}},
                    datasets: [
                      { label: 'Wydatki miesięczne', data: {{TREND_DATA}},
                        borderColor: '#3b82f6', backgroundColor: 'rgba(59,130,246,.1)',
                        fill: true, tension: 0.3, pointRadius: 4 },
                      { label: 'Śr. krocząca 3M', data: {{MA_DATA}},
                        borderColor: '#ef4444', borderDash: [6,3],
                        tension: 0.3, fill: false, pointRadius: 2 }
                    ]
                  }
                });

                new Chart(document.getElementById('predChart'), {
                  type: 'bar',
                  data: {
                    labels: {{PRED_LABELS}},
                    datasets: [{
                      label: 'Prognoza (PLN)',
                      data: {{PRED_DATA}},
                      backgroundColor: 'rgba(139,92,246,.65)',
                      borderColor: '#7c3aed', borderWidth: 2
                    }]
                  }
                });
              </script>
            </body>
            </html>
            """;
}
