# Analizator Wydatków – Programowanie Funkcyjne (Java 21)

Konsolowy analizator wydatków osobistych z wykrywaniem anomalii (IQR / odchylenie standardowe),
predykcją (regresja liniowa MKW, EMA) i eksportem interaktywnego raportu HTML.
Projekt zaliczeniowy z Programowania Funkcyjnego – nacisk na Stream API, records,
sealed interfaces i separację czystej logiki od I/O.

## Uruchomienie

**Wymagania:** Java 21+

```bash
# Jeśli Maven zainstalowany lokalnie:
mvn test
mvn package -q
java -jar target/analizator-wydatkow-1.0-SNAPSHOT-fat.jar

# Jeśli Maven NIE jest zainstalowany – użyj Maven Wrapper (pobierze Maven automatycznie):
./mvnw test
./mvnw package -q
java -jar target/analizator-wydatkow-1.0-SNAPSHOT-fat.jar
```

Po uruchomieniu aplikacja wypisuje wyniki w konsoli i zapisuje plik `raport.html`
w bieżącym katalogu – otwórz go w przeglądarce, aby zobaczyć interaktywne wykresy.

```bash
# Alternatywnie: wczytaj dane z JSON zamiast CSV
java -jar target/analizator-wydatkow-1.0-SNAPSHOT-fat.jar expenses.json
```

## Struktura projektu

```
src/main/java/pl/fp/wydatki/
├── model/          – niemutowalne rekordy (Expense, Anomaly, Prediction, …)
├── io/             – I/O: czytanie CSV/JSON, zapis HTML
├── processing/     – filtrowanie i agregacja (czyste funkcje)
├── analysis/       – anomalie, trendy, predykcja (czyste funkcje)
├── visualization/  – generowanie HTML z Chart.js (czysta funkcja)
└── Main.java       – CLI entry-point

src/main/resources/
├── expenses.csv    – 651 syntetycznych transakcji (2024, 6 kategorii + 10 anomalii)
└── expenses.json   – te same dane w formacie JSON

src/test/java/pl/fp/wydatki/
├── processing/     – ExpenseFilterTest, ExpenseAggregatorTest
└── analysis/       – AnomalyDetectorTest, ExpensePredictorTest
                      47 testów, 0 błędów
```

## Kluczowe techniki FP

| Technika | Gdzie |
|---|---|
| `record` – niemutowalne klasy danych | `model/` |
| `sealed interface` – algebraiczny typ sumy | `model/AnomalyType.java` |
| Kompozycja `Predicate` (`.and()` / `.or()` / fold) | `processing/Filters.java` |
| `Stream` + `Collectors.groupingBy` + `collectingAndThen` | `processing/ExpenseAggregator.java` |
| Funkcja wyższego rzędu (`Function<A,B>` jako parametr) | `analysis/AnomalyDetector.java` |
| `switch expression` zwracający lambda | `processing/ExpenseAggregator.java` |
| Separacja I/O od czystej logiki | cała architektura pakietów |
