package pl.fp.wydatki.io;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import pl.fp.wydatki.model.Category;
import pl.fp.wydatki.model.Expense;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * FP: efekt uboczny (czytanie I/O) jest świadomie izolowany w tym pakiecie.
 *
 * Transformacja wiersza CSV → Expense jest wydzielona jako package-private
 * stała funkcji (ROW_TO_EXPENSE), dzięki czemu można ją niezależnie testować
 * bez angażowania systemu plików.
 */
public final class CsvReader {
    private CsvReader() {}

    // Czysta funkcja: String[] → Expense (testowalna bez I/O)
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
                    .skip(1)  // nagłówek
                    .map(ROW_TO_EXPENSE)
                    .toList();
        }
    }
}
