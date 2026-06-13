# Raport z projektu: Analizator Wydatków z Predykcją i Wykrywaniem Anomalii
### Programowanie Funkcyjne – Java 21

---

## 1. Zakres funkcjonalny

Projekt to konsolowa aplikacja do analizy wydatków osobistych. Wczytuje dane z CSV lub JSON, grupuje je po kategoriach i okresach, wykrywa podejrzanie duże transakcje i generuje predykcję na kolejne miesiące. Wyniki trafiają do interaktywnego raportu HTML.

| Nr | Funkcja | Moduł |
|----|---------|-------|
| F1 | Import danych z CSV lub JSON | `io.CsvReader`, `io.JsonReader` |
| F2 | Sumowanie wydatków wg kategorii | `processing.ExpenseAggregator` |
| F3 | Sumowanie wg okresu: dzień / tydzień ISO / miesiąc | `processing.ExpenseAggregator` |
| F4 | Filtrowanie po dacie, kategorii, kwocie i słowie kluczowym | `processing.Filters`, `ExpenseFilter` |
| F5 | Wykrywanie anomalii metodą IQR (Tukey's fence) | `analysis.AnomalyDetector` |
| F6 | Wykrywanie anomalii metodą Z-score (2σ) | `analysis.AnomalyDetector` |
| F7 | Trendy miesięczne i tygodniowe + stopa wzrostu między okresami | `analysis.TrendAnalyzer` |
| F8 | Predykcja regresją liniową MKW z miarą pewności R² | `analysis.ExpensePredictor` |
| F9 | Predykcja wykładniczą średnią kroczącą (EMA) | `analysis.ExpensePredictor` |
| F10 | Eksport raportu HTML z wykresami Chart.js | `visualization.HtmlChartExporter` |

Jako dane testowe wygenerowałam 651 syntetycznych transakcji z 2024 roku w sześciu kategoriach (JEDZENIE, TRANSPORT, ROZRYWKA, RACHUNKI, ZDROWIE, ZAKUPY). Żeby przetestować wykrywanie anomalii, celowo wstawiłam 10 transakcji znacznie odbiegających od normy – np. laptop za 1 750 PLN czy wynajem auta za 980 PLN.

---

## 2. Architektura projektu

Projekt podzieliłam na pięć pakietów według jednej prostej zasady: efekty uboczne (czytanie pliku, zapis na dysk) mają prawo istnieć tylko w `io/` i `Main.java`. Cała reszta – `processing/`, `analysis/`, `visualization/` – to czyste funkcje, które nie wiedzą o istnieniu systemu plików.

```
pl.fp.wydatki/
├── model/          – niemutowalne rekordy
├── io/             – I/O: jedyne miejsce efektów ubocznych
├── processing/     – filtrowanie i agregacja
├── analysis/       – anomalie, trendy, predykcja
├── visualization/  – generowanie HTML
└── Main.java       – orkiestruje I/O ↔ logika
```

Przepływ danych jest zawsze jednokierunkowy:

```
Plik CSV/JSON
     ↓  (I/O)
List<Expense>            ← tworzona raz, nigdy nie modyfikowana
     ↓  (czyste funkcje)
List<CategorySummary>
List<TrendPoint>
List<Anomaly>
List<Prediction>
     ↓  (czysta funkcja)
String (HTML)
     ↓  (I/O)
raport.html
```

To podejście jest zainspirowane zasadą *"IO at the edges"* z Haskella – i bardzo ułatwia testowanie, bo żaden test nie musi otwierać pliku ani mockować systemu plików.

---

## 3. Zastosowane techniki programowania funkcyjnego

### 3.1 Niemutowalne klasy danych – `record`

Zamiast klas z getterami i setterami wszystkie obiekty domenowe to `record`. Kompilator sam generuje konstruktor, akcesory, `equals`, `hashCode` i `toString` – i nie generuje żadnych setterów, więc obiekt nie może zostać zmodyfikowany po powstaniu.

```java
public record Expense(
        int        id,
        LocalDate  date,
        BigDecimal amount,
        Category   category,
        String     description
) {}
```

W projekcie zdefiniowałam 7 rekordów: `Expense`, `CategorySummary`, `Anomaly`, `TrendPoint`, `Prediction` oraz zagnieżdżone `AnomalyType.StdDev` i `AnomalyType.IQR`.

### 3.2 Sealed interface jako algebraiczny typ sumy

Typ `AnomalyType` ma dwa warianty – anomalia wykryta przez IQR albo przez odchylenie standardowe. Zamiast trzymać metodę jako `String` lub `enum`, użyłam `sealed interface`. Dzięki temu każdy wariant niesie swoje własne dane parametryczne i kompilator wymusi obsługę nowego wariantu wszędzie, gdyby ktoś dodał go do `permits`.

```java
public sealed interface AnomalyType permits AnomalyType.StdDev, AnomalyType.IQR {
    String label();

    record StdDev(double sigmaMultiplier, double threshold) implements AnomalyType {
        public String label() {
            return String.format("StdDev (%.1fσ, próg=%.2f)", sigmaMultiplier, threshold);
        }
    }
    record IQR(double iqrMultiplier, double upperFence) implements AnomalyType {
        public String label() {
            return String.format("IQR (x%.1f, górny płot=%.2f)", iqrMultiplier, upperFence);
        }
    }
}
```

### 3.3 Kompozycja predykatów

`Filters` to klasa z metodami zwracającymi `Predicate<Expense>`. Każdy predykat można potem dowolnie składać przez `.and()`, `.or()`, `.negate()` bez tworzenia nowych klas.

```java
public static Predicate<Expense> byAmountRange(BigDecimal min, BigDecimal max) {
    return byMinAmount(min).and(byMaxAmount(max));
}
```

Ciekawszy przypadek to filtrowanie po liście kategorii – zdecydowałam się na fold po predykatach zamiast prostego `categories.contains(...)`, żeby pokazać kompozycję funkcyjną:

```java
Predicate<Expense> combined = categories.stream()
        .map(Filters::byCategory)
        .reduce(__ -> false, (a, b) -> a.or(b));
```

To funkcyjny odpowiednik `false OR cat1 OR cat2 OR ...` – bez żadnej zmiennej pośredniej.

### 3.4 Stream API i Collectors – agregacja bez mutacji

Agregacja wydatków wg kategorii nie używa żadnej zmiennej ani kolekcji pośredniej. Cały GROUP BY + obliczenie sumy/średniej/liczby to jeden pipeline:

```java
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
            .toList();   // Stream.toList() → lista niemodyfikowalna
}
```

`collectingAndThen` pozwala od razu przetransformować zebraną grupę w rekord – nie ma etapu "zbierz do mapy, potem iteruj po mapie".

### 3.5 Switch expression jako wartość

W `trendByPeriod` wybór funkcji kluczowania jest wyrażeniem – nie blokiem if-else ze zmienną pośrednią. Funkcja `keyFn` to tu pełnoprawna wartość, którą można przekazać dalej:

```java
Function<Expense, String> keyFn = switch (period) {
    case DAY   -> e -> e.date().toString();
    case WEEK  -> e -> e.date().getYear() + "-W" +
                       String.format("%02d",
                           e.date().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    case MONTH -> e -> DateTimeFormatter.ofPattern("yyyy-MM").format(e.date());
};
return aggregateByKey(expenses, keyFn);
```

### 3.6 Funkcja wyższego rzędu – strategia przez `Function`

`AnomalyDetector` ma wspólny szkielet `detectPerCategory`, który przyjmuje strategię wykrywania jako parametr `Function<List<Expense>, List<Anomaly>>`. Dzięki temu obie metody (IQR i StdDev) nie powtarzają kodu grupowania po kategoriach:

```java
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

public static List<Anomaly> detectByStdDev(List<Expense> expenses) {
    return detectPerCategory(expenses, AnomalyDetector::stdDevAnomalies);
}

public static List<Anomaly> detectByIQR(List<Expense> expenses) {
    return detectPerCategory(expenses, AnomalyDetector::iqrAnomalies);
}
```

Wzorzec Strategy bez hierarchii klas, bez dziedziczenia – tylko referencja do metody.

### 3.7 IntStream.range jako generator indeksów

Przy obliczaniu średniej kroczącej potrzebowałam indeksu, żeby wiedzieć jaka jest szerokość okna. Zamiast pętli `for` użyłam `IntStream.range` – wewnątrz `mapToObj` mam dostęp do indeksu bez żadnej zmiennej zewnętrznej:

```java
public static List<TrendPoint> movingAverage(List<TrendPoint> trend, int windowSize) {
    return IntStream.range(0, trend.size())
            .mapToObj(i -> {
                int from = Math.max(0, i - windowSize + 1);
                BigDecimal sum = trend.subList(from, i + 1).stream()
                        .map(TrendPoint::total)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal avg = sum.divide(
                        BigDecimal.valueOf(i - from + 1), 2, RoundingMode.HALF_UP);
                return new TrendPoint(trend.get(i).periodLabel(), avg, trend.get(i).count());
            })
            .toList();
}
```

`IntStream.rangeClosed` jest tak samo używany w predykcji do generowania kolejnych miesięcy.

### 3.8 Czysta funkcja parsowania w warstwie I/O

W `CsvReader` transformacja wiersza CSV na `Expense` jest wydzielona jako package-private stała – można ją testować bezpośrednio, bez otwierania żadnego pliku:

```java
// Czysta funkcja: String[] → Expense
static final Function<String[], Expense> ROW_TO_EXPENSE = row -> new Expense(
        Integer.parseInt(row[0].trim()),
        LocalDate.parse(row[1].trim()),
        new BigDecimal(row[2].trim()),
        Category.fromString(row[3].trim()),
        row[4].trim()
);

public static List<Expense> read(Reader reader) throws IOException, CsvException {
    try (CSVReader csv = new CSVReader(reader)) {
        return csv.readAll().stream()
                .skip(1)
                .map(ROW_TO_EXPENSE)
                .toList();
    }
}
```

Efekt uboczny (otwarcie pliku) jest w jednej linii. Reszta to czysta transformacja potoku.

### 3.9 Generowanie HTML jako czysta transformacja

`HtmlChartExporter.export` to czysta funkcja – bierze dane, zwraca `String`. Nie dotyka dysku. Zapis do pliku należy do `ResultExporter`, żeby I/O zostało po swojej stronie granicy.

```java
public static String export(
        List<CategorySummary> summaries,
        List<TrendPoint> monthlyTrend,
        List<TrendPoint> movingAvg,
        List<Anomaly> anomalies,
        List<Prediction> predictions) {

    return TEMPLATE
            .replace("{{CATEGORY_LABELS}}", jsonLabels(summaries, s -> quote(s.category().name())))
            .replace("{{CATEGORY_DATA}}",   jsonValues(summaries, s -> s.total().toPlainString()))
            // ...
            .replace("{{ANOMALY_ROWS}}",    anomalyRows(anomalies));
}
```

Szablon HTML z wbudowanym Chart.js jest stałą w tej samej klasie – Java generuje string, przeglądarka renderuje wykres.

---

## 4. Testy jednostkowe

Napisałam 47 testów JUnit 5 w czterech klasach. Większość z nich wprost sprawdza właściwości, które w FP są kluczowe – determinizm i brak efektów ubocznych.

**Determinizm** – ta sama funkcja wywołana dwa razy z tymi samymi danymi musi dać identyczny wynik:

```java
@Test
void filter_isDeterministic() {
    Predicate<Expense> pred = Filters.byCategory(Category.JEDZENIE);
    assertEquals(ExpenseFilter.filter(SAMPLE, pred),
                 ExpenseFilter.filter(SAMPLE, pred));
}
```

**Brak efektów ubocznych** – filtrowanie nie może modyfikować listy wejściowej:

```java
@Test
void filter_doesNotMutateInput() {
    List<Expense> originalCopy = List.copyOf(SAMPLE);
    ExpenseFilter.filter(SAMPLE, Filters.byMinAmount(new BigDecimal("100.00")));
    assertEquals(originalCopy, SAMPLE);
}
```

Kilka testów sprawdza też poprawność matematyczną – np. że regresja liniowa na danych 100→200→300→400 daje dokładnie 500 PLN z R²=1.0:

```java
@Test
void predictLinear_exactValueForPerfectLinearTrend() {
    Prediction p = ExpensePredictor.predictLinear(LINEAR, 1).get(0);
    assertEquals(new BigDecimal("500.00"), p.predictedAmount());
}

@Test
void predictLinear_r2EqualsOneForPerfectFit() {
    Prediction p = ExpensePredictor.predictLinear(LINEAR, 1).get(0);
    assertEquals(1.0, p.confidence(), 1e-9);
}
```

Wyniki: **47 testów, 0 błędów**.

| Klasa testowa | Testów | Co sprawdza |
|---|---|---|
| `ExpenseFilterTest` | 10 | filtrowanie, kompozycja predykatów, brak mutacji, niemutowalny wynik |
| `ExpenseAggregatorTest` | 11 | sumy, liczba, średnia, kolejność, determinizm |
| `AnomalyDetectorTest` | 14 | IQR, StdDev, brak fałszywych pozytywów, sealed interface, funkcje statystyczne |
| `ExpensePredictorTest` | 12 | regresja liniowa, R², EMA, brak ujemnych kwot |

---

## 5. Wnioski

Największą zaletą stylu funkcyjnego okazała się dla mnie **testowalność**. Funkcje w `processing/` i `analysis/` nie mają żadnych zależności zewnętrznych – można je wywołać z dowolnymi danymi i od razu zobaczyć wynik. Nie trzeba konfigurować żadnego środowiska, nie trzeba mockować bazy ani systemu plików. Wszystkie 47 testów to proste `assertEquals` na danych w pamięci.

Drugie co mi się podobało to **kompozycja zamiast dziedziczenia**. Wzorzec Strategy w `AnomalyDetector` – przekazanie funkcji `Function<List<Expense>, List<Anomaly>>` jako parametru – jest dużo czytelniejszy niż tworzenie hierarchii klas `StdDevStrategy implements AnomalyStrategy`. Mniej kodu, ta sama elastyczność.

Były też ograniczenia, które trochę utrudniały pracę. Java nie przepuszcza checked exceptions przez lambdy, więc tam gdzie parsowanie mogło się nie udać (CSV, JSON), musiałam trzymać I/O poza strumieniami. Trochę nieeleganckie, ale nie dało się tego łatwo obejść bez własnych wrapperów.

Wyczerpujące `switch` dla sealed interfaces działa w Javie 21 jako pełna funkcja (w 17 było to preview), więc korzystałam z niego m.in. w `ExpenseAggregator` do wyboru funkcji kluczowania. To jeden z tych momentów gdzie Java faktycznie zaczyna przypominać język funkcyjny.

Ogólnie – styl funkcyjny sprawdził się w tym projekcie dobrze, szczególnie w warstwie analizy danych gdzie chodzi o transformacje: wejście → wyjście, bez stanu. Gdybym robiła projekt z interfejsem graficznym albo bazą danych, pewnie ta równowaga między FP a OOP wyglądałaby inaczej.
