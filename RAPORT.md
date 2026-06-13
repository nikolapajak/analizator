# Raport z projektu: Analizator Wydatków z Predykcją i Wykrywaniem Anomalii
### Programowanie Funkcyjne – Java 21

---

## 1. Zakres funkcjonalny

Projekt implementuje konsolowy analizator wydatków osobistych z graficznym raportem HTML.
System realizuje następujące funkcje:

| Nr | Funkcja | Moduł |
|----|---------|-------|
| F1 | Import danych z pliku CSV lub JSON | `io.CsvReader`, `io.JsonReader` |
| F2 | Kategoryzacja wydatków (6 kategorii) i sumowanie wg kategorii | `processing.ExpenseAggregator` |
| F3 | Sumowanie wg okresu: dzień / tydzień ISO / miesiąc | `processing.ExpenseAggregator` |
| F4 | Filtrowanie po dacie, kategorii, kwocie i słowie kluczowym | `processing.Filters`, `ExpenseFilter` |
| F5 | Wykrywanie anomalii metodą IQR (Tukey's fence) | `analysis.AnomalyDetector` |
| F6 | Wykrywanie anomalii metodą odchylenia standardowego (Z-score 2σ) | `analysis.AnomalyDetector` |
| F7 | Analiza trendów miesięcznych i tygodniowych + stopa wzrostu | `analysis.TrendAnalyzer` |
| F8 | Predykcja metodą regresji liniowej MKW z miarą R² | `analysis.ExpensePredictor` |
| F9 | Predykcja metodą wykładniczej średniej kroczącej (EMA) | `analysis.ExpensePredictor` |
| F10 | Eksport interaktywnego raportu HTML z wykresami (Chart.js) | `visualization.HtmlChartExporter` |

Dataset testowy zawiera **651 syntetycznych transakcji** z okresu 2024-01-01 – 2024-12-31,
w sześciu kategoriach (JEDZENIE, TRANSPORT, ROZRYWKA, RACHUNKI, ZDROWIE, ZAKUPY),
z celowo wprowadzonymi **10 anomaliami** (np. laptop 1 750 PLN, wynajem auta 980 PLN).

---

## 2. Architektura projektu

### 2.1 Struktura pakietów

```
pl.fp.wydatki/
├── model/          – niemutowalne rekordy (Expense, CategorySummary, Anomaly, …)
├── io/             – JEDYNE miejsce efektów ubocznych (czytanie plików, zapis HTML)
├── processing/     – czyste funkcje filtrowania i agregacji
├── analysis/       – czyste funkcje analizy trendów, anomalii i predykcji
├── visualization/  – czysta funkcja generowania HTML
└── Main.java       – punktstykowy: orkiestruje I/O ↔ czysta logika
```

### 2.2 Kluczowa zasada architektoniczna: separacja I/O od logiki czystej

Projekt stosuje wzorzec znany z Haskella jako *"IO monad at the edges"*:
efekty uboczne (czytanie pliku, zapis na dysk) są celowo ograniczone do pakietu `io/`
i metody `main`. Wszystkie pakiety `processing/`, `analysis/` i `visualization/`
zawierają **wyłącznie czyste funkcje** – nigdy nie odczytują stanu zewnętrznego
ani nie modyfikują żadnych współdzielonych danych.

Przepływ danych jest jednokierunkowy:

```
Plik CSV/JSON
     ↓  (I/O – CsvReader / JsonReader)
List<Expense>   ← niemutowalna, tworzona raz
     ↓  (czyste funkcje)
List<CategorySummary>  ←  ExpenseAggregator.summarizeByCategory(expenses)
List<TrendPoint>       ←  TrendAnalyzer.monthlyTrend(expenses)
List<Anomaly>          ←  AnomalyDetector.detectByIQR(expenses)
List<Prediction>       ←  ExpensePredictor.predictLinear(monthly, 3)
     ↓  (czysta funkcja)
String (HTML)          ←  HtmlChartExporter.export(...)
     ↓  (I/O – ResultExporter)
raport.html
```

---

## 3. Zastosowane techniki programowania funkcyjnego

### 3.1 Niemutowalne klasy danych – `record`

Zamiast klas JavaBean z getterami i setterami, wszystkie obiekty domenowe
są zadeklarowane jako `record`. Kompilator automatycznie generuje konstruktor,
akcesory, `equals`, `hashCode` i `toString` – i **nie generuje setterów**.
Obiekt jest niemodyfikowalny od momentu powstania.

```java
// model/Expense.java
public record Expense(
        int        id,
        LocalDate  date,
        BigDecimal amount,
        Category   category,
        String     description
) {}
```

Użycie:

```java
Expense e = new Expense(1, LocalDate.of(2024,1,10),
                        new BigDecimal("55.00"), Category.JEDZENIE, "Lidl");
e.amount();   // akcesor – tylko odczyt
// e.amount = ...  ← błąd kompilacji – niemożliwe
```

W projekcie zdefiniowano **7 rekordów**: `Expense`, `CategorySummary`, `Anomaly`,
`TrendPoint`, `Prediction` oraz zagnieżdżone `AnomalyType.StdDev` i `AnomalyType.IQR`.

### 3.2 Sealed interface jako algebraiczny typ sumy

`AnomalyType` reprezentuje dwa możliwe warianty wykrytej anomalii.
Zamiast pola-flagi (`String method = "IQR"`) użyto sealed interface:

```java
// model/AnomalyType.java
public sealed interface AnomalyType permits AnomalyType.StdDev, AnomalyType.IQR {

    String label();   // polimorfizm zamiast if-else

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

Kluczowa właściwość: kompilator **wymusza wyczerpanie wariantów** przy `instanceof`.
Dodanie nowego wariantu do `permits` spowoduje błąd kompilacji w każdym miejscu,
które nie obsługuje nowego przypadku – jest to odpowiednik *exhaustive pattern matching*
z języków funkcyjnych (ML, Haskell, Scala).

### 3.3 Fabryka predykatów i kompozycja funkcji

Klasa `Filters` to fabryka **wartości funkcyjnych** – każda metoda zwraca
`Predicate<Expense>`, który można składać operatorami `.and()`, `.or()`, `.negate()`.

```java
// processing/Filters.java  (fragment)
public static Predicate<Expense> byCategory(Category category) {
    return e -> e.category() == category;
}

public static Predicate<Expense> byAmountRange(BigDecimal min, BigDecimal max) {
    // kompozycja przez .and() – bez jawnego if-else
    return byMinAmount(min).and(byMaxAmount(max));
}
```

W `Main.java` predykaty są łączone dynamicznie:

```java
List<Expense> filtered = ExpenseFilter.filter(expenses,
        Filters.byMinAmount(new BigDecimal("300.00"))
               .and(Filters.byCategory(Category.ZAKUPY)));
```

Metoda `filterByCategories` demonstruje **fold po predykatach** –
redukuje listę kategorii do jednego złożonego predykatu bez pętli:

```java
// processing/ExpenseFilter.java
Predicate<Expense> combined = categories.stream()
        .map(Filters::byCategory)            // Stream<Predicate<Expense>>
        .reduce(__ -> false, (a, b) -> a.or(b));  // złóż: false OR cat1 OR cat2 ...
```

Jest to funkcyjny odpowiednik `fold` / `reduce` z języków takich jak Haskell czy Clojure.

### 3.4 Stream API i Collectors – agregacja bez mutacji

Agregacja wydatków nie używa żadnej zmiennej ani kolekcji pośredniej:

```java
// processing/ExpenseAggregator.java  (fragment)
public static List<CategorySummary> summarizeByCategory(List<Expense> expenses) {
    return expenses.stream()
            .collect(Collectors.groupingBy(
                    Expense::category,               // referencja do metody
                    Collectors.collectingAndThen(
                            Collectors.toList(),
                            ExpenseAggregator::toCategorySummary  // transformacja grupy
                    )
            ))
            .values().stream()
            .sorted(Comparator.comparing(CategorySummary::total).reversed())
            .toList();  // Stream.toList() → lista niemodyfikowalna (Java 16+)
}
```

`Collectors.groupingBy` + `collectingAndThen` to funkcyjny odpowiednik
`GROUP BY + SELECT` z SQL – transformacja opisana deklaratywnie, bez jawnych pętli.
`Stream.toList()` gwarantuje, że wynik jest niemodyfikowalny.

### 3.5 Switch expression jako wyrażenie (nie instrukcja)

W `ExpenseAggregator.trendByPeriod` wybór funkcji klucza grupowania jest
wyrażeniem, które zwraca wartość – nie instrukcją ze zmienną pośrednią:

```java
// processing/ExpenseAggregator.java
Function<Expense, String> keyFn = switch (period) {
    case DAY   -> e -> e.date().toString();
    case WEEK  -> e -> e.date().getYear() + "-W" +
                       String.format("%02d",
                           e.date().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    case MONTH -> e -> DateTimeFormatter.ofPattern("yyyy-MM").format(e.date());
};
return aggregateByKey(expenses, keyFn);
```

Funkcja `keyFn` jest tu **wartością pierwszoklasową** – wybraną spośród trzech lambd
i przekazaną dalej. To idiomat z programowania funkcyjnego niedostępny w klasycznym `if-else`.

### 3.6 Funkcja wyższego rzędu – Strategy przez Function

`AnomalyDetector.detectPerCategory` przyjmuje strategię wykrywania
jako parametr typu `Function<List<Expense>, List<Anomaly>>`:

```java
// analysis/AnomalyDetector.java
private static List<Anomaly> detectPerCategory(
        List<Expense> expenses,
        Function<List<Expense>, List<Anomaly>> detector) {  // strategia jako wartość

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

Wzorzec Strategy zrealizowany przez funkcję wyższego rzędu zamiast dziedziczenia –
brak klas `StdDevStrategy implements AnomalyStrategy`, brak hierarchii obiektów.

### 3.7 IntStream.range jako funkcyjny generator indeksów

Średnia krocząca i predykcja operują na indeksach bez zmiennych pętli:

```java
// analysis/TrendAnalyzer.java
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

`IntStream.rangeClosed` jest analogicznie używany w `ExpensePredictor.predictLinear`
do generowania kolejnych miesięcy predykcji.

### 3.8 Separacja czystej funkcji od I/O w warstwie IO

W `CsvReader` transformacja wiersza CSV na `Expense` jest wydzielona
jako **package-private stała funkcyjna** – testowalna bez angażowania systemu plików:

```java
// io/CsvReader.java
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
                .skip(1)          // nagłówek
                .map(ROW_TO_EXPENSE)   // aplikacja czystej funkcji
                .toList();
    }
}
```

Efekt uboczny (otwarcie pliku) jest ograniczony do jednej linii `new CSVReader(reader)`.
Reszta metody to czysta transformacja potoku danych.

### 3.9 Generowanie HTML jako czysta transformacja

`HtmlChartExporter.export` jest czystą funkcją `(dane) → String`:

```java
// visualization/HtmlChartExporter.java  (fragment)
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

Metoda nie dotyka systemu plików – zwraca `String`. Zapis do pliku jest obowiązkiem
wywołującego (`ResultExporter.writeHtml`), co jest bezpośrednią implementacją
zasady *"push I/O to the edges"*.

---

## 4. Testy jednostkowe – weryfikacja czystości i determinizmu

Napisano **47 testów JUnit 5** w 4 klasach testowych, które wprost weryfikują
właściwości wymagane przez programowanie funkcyjne.

### 4.1 Determinizm – ta sama funkcja zawsze daje ten sam wynik

```java
// ExpenseFilterTest.java
@Test
void filter_isDeterministic() {
    Predicate<Expense> pred = Filters.byCategory(Category.JEDZENIE);
    assertEquals(ExpenseFilter.filter(SAMPLE, pred),
                 ExpenseFilter.filter(SAMPLE, pred));
}

// ExpensePredictorTest.java
@Test
void predictLinear_isDeterministic() {
    assertEquals(
        ExpensePredictor.predictLinear(LINEAR, 3),
        ExpensePredictor.predictLinear(LINEAR, 3));
}
```

### 4.2 Brak efektów ubocznych – lista wejściowa nie jest modyfikowana

```java
// ExpenseFilterTest.java
@Test
void filter_doesNotMutateInput() {
    List<Expense> originalCopy = List.copyOf(SAMPLE);
    ExpenseFilter.filter(SAMPLE, Filters.byMinAmount(new BigDecimal("100.00")));
    assertEquals(originalCopy, SAMPLE, "Lista wejściowa nie może zostać zmodyfikowana");
}
```

### 4.3 Niemodyfikowalność wyników – Stream.toList()

```java
// ExpenseFilterTest.java
@Test
void filter_resultIsUnmodifiable() {
    List<Expense> result = ExpenseFilter.filter(SAMPLE, Filters.byCategory(Category.JEDZENIE));
    assertThrows(UnsupportedOperationException.class,
            () -> result.add(SAMPLE.get(0)));
}
```

### 4.4 Poprawność matematyczna funkcji statystycznych

```java
// AnomalyDetectorTest.java
@Test
void stdDev_knownValue() {
    // Klasyczny przykład: {2,4,4,4,5,5,7,9} → stdDev = 2.0
    double[] v = {2, 4, 4, 4, 5, 5, 7, 9};
    assertEquals(2.0, AnomalyDetector.stdDev(v, AnomalyDetector.mean(v)), 1e-9);
}

// ExpensePredictorTest.java
@Test
void predictLinear_exactValueForPerfectLinearTrend() {
    // 100,200,300,400 → slope=100, intercept=100 → predict(x=4)=500
    Prediction p = ExpensePredictor.predictLinear(LINEAR, 1).get(0);
    assertEquals(new BigDecimal("500.00"), p.predictedAmount());
}

@Test
void predictLinear_r2EqualsOneForPerfectFit() {
    Prediction p = ExpensePredictor.predictLinear(LINEAR, 1).get(0);
    assertEquals(1.0, p.confidence(), 1e-9);
}
```

### 4.5 Wyniki testów

```
Tests run: 47, Failures: 0, Errors: 0, Skipped: 0  →  BUILD SUCCESS
```

| Klasa testowa | Testów | Weryfikuje |
|---|---|---|
| `ExpenseFilterTest` | 10 | filtrowanie, kompozycja predykatów, brak mutacji, niemutowalny wynik |
| `ExpenseAggregatorTest` | 11 | sumy, liczba, średnia, kolejność, determinizm |
| `AnomalyDetectorTest` | 14 | IQR, StdDev, brak fałszywych pozytywów, sealed interface, statystyki |
| `ExpensePredictorTest` | 12 | regresja liniowa, R², EMA, brak ujemnych kwot |

---

## 5. Wnioski

### 5.1 Co przyniosło styl funkcyjny

**Testowalność bez mocków.** Czyste funkcje nie mają zależności od stanu zewnętrznego,
więc wszystkie 47 testów operuje bezpośrednio na danych – nie ma potrzeby konfigurowania
środowiska ani stosowania frameworków do mockowania.

**Lokalność rozumowania.** Każda metoda w `processing/` i `analysis/` jest samowystarczalna:
wynik zależy wyłącznie od parametrów. Przy debugowaniu można uruchomić funkcję izolowanie
z dowolnymi danymi i przewidzieć wynik bez śledzenia stanu aplikacji.

**Kompozycja zamiast dziedziczenia.** Wzorce Strategy i Builder zostały zastąpione
przez przekazywanie funkcji (`Function<A,B>`, `Predicate<T>`). Przykładem jest
`detectPerCategory` przyjmujący strategię wykrywania jako parametr –
brak klas pośrednich i hierarchii dziedziczenia.

**Niezmienniczość danych.** Wszystkie `record`y i wyniki `Stream.toList()` są niemodyfikowalne.
Umożliwia to bezpieczne współdzielenie list między wieloma wywołaniami funkcji
(np. `expenses` jest przekazywane do kilku niezależnych potoków przetwarzania).

### 5.2 Ograniczenia FP w Javie

Java nie jest językiem funkcyjnym z natury. Kilka ograniczeń wpłynęło na projekt:

- **Checked exceptions** nie współpracują ze strumieniami – stąd `CsvReader.read`
  musi korzystać z `try-with-resources` poza strumieniem; nie można użyć go
  bezpośrednio wewnątrz `map()`.

- **Brak wyczerpującego switch dla sealed interfaces** w Java 17 (dodany dopiero w Java 21) –
  rozwiązaniem było przeniesienie logiki opisu wariantu do metody `label()` na interfejsie.

- **`BigDecimal` jako typ wartościowy** – `reduce(BigDecimal.ZERO, BigDecimal::add)`
  jest idiomatyczne, ale `BigDecimal::add` jest metodą instancji (zwracającą nową wartość),
  co wymaga świadomości różnicy z matematycznym `+`.

### 5.3 Podsumowanie zastosowanych technik FP

| Technika | Implementacja w projekcie |
|---|---|
| Niemutowalne struktury danych | `record Expense`, `record Anomaly`, `Stream.toList()` |
| Algebraiczne typy sumy | `sealed interface AnomalyType permits StdDev, IQR` |
| Funkcje jako wartości | `Function<String[], Expense>`, `Predicate<Expense>` jako pola/parametry |
| Funkcje wyższego rzędu | `detectPerCategory(expenses, Function<…>)` |
| Kompozycja funkcji | `Filters.byMinAmount(x).and(Filters.byCategory(c))` |
| Fold / reduce | `categories.stream().map(…).reduce(false, Predicate::or)` |
| Czyste funkcje | wszystkie metody w `processing/`, `analysis/`, `visualization/` |
| Separacja I/O | efekty uboczne wyłącznie w `io/` i `Main.java` |
| Switch expression jako wartość | `Function<Expense,String> keyFn = switch (period) { … }` |
| Deklaratywna agregacja | `Collectors.groupingBy` + `collectingAndThen` + `Comparator` |

---

*Raport przygotowany w ramach projektu z Programowania Funkcyjnego.*
*Kod źródłowy: `src/main/java/pl/fp/wydatki/`*
*Testy: `src/test/java/pl/fp/wydatki/` — `mvn test` → 47 testów, 0 błędów.*
