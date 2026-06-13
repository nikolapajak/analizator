package pl.fp.wydatki.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import pl.fp.wydatki.model.Category;
import pl.fp.wydatki.model.Expense;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * FP: parsowanie JSON – efekty uboczne tylko w read().
 *
 * ObjectMapper jest shared (bezpieczny wielowątkowo po konfiguracji),
 * mapToExpense to czysta funkcja Map → Expense, testowalnie niezależna od I/O.
 */
public final class JsonReader {
    private JsonReader() {}

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // Czysta funkcja: Map<String,Object> → Expense
    static final Function<Map<String, Object>, Expense> MAP_TO_EXPENSE = m -> new Expense(
            (Integer) m.get("id"),
            LocalDate.parse((String) m.get("date")),
            new BigDecimal(m.get("amount").toString()),
            Category.fromString((String) m.get("category")),
            (String) m.get("description")
    );

    @SuppressWarnings("unchecked")
    public static List<Expense> read(Reader reader) throws IOException {
        List<Map<String, Object>> raw = MAPPER.readValue(reader, List.class);
        return raw.stream()
                .map(MAP_TO_EXPENSE)
                .toList();
    }
}
