package pl.fp.wydatki.processing;

import pl.fp.wydatki.model.Category;
import pl.fp.wydatki.model.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Predicate;

/**
 * FP: fabryka predykatów – każda metoda to czysta funkcja zwracająca Predicate<Expense>.
 *
 * Predykaty są:
 *  - niemutowalne (lambdy nie mają stanu),
 *  - komponowalne przez .and() / .or() / .negate() bez tworzenia nowych klas,
 *  - wielokrotnego użytku (można przekazywać jako wartości funkcji wyższego rzędu).
 *
 * Klasa jest final + prywatny konstruktor = utilities-only, brak instancji.
 */
public final class Filters {
    private Filters() {}

    public static Predicate<Expense> byCategory(Category category) {
        return e -> e.category() == category;
    }

    public static Predicate<Expense> byDateRange(LocalDate from, LocalDate to) {
        return e -> !e.date().isBefore(from) && !e.date().isAfter(to);
    }

    public static Predicate<Expense> byMinAmount(BigDecimal min) {
        return e -> e.amount().compareTo(min) >= 0;
    }

    public static Predicate<Expense> byMaxAmount(BigDecimal max) {
        return e -> e.amount().compareTo(max) <= 0;
    }

    public static Predicate<Expense> byAmountRange(BigDecimal min, BigDecimal max) {
        // FP: kompozycja przez .and() – logiczne AND bez if-else
        return byMinAmount(min).and(byMaxAmount(max));
    }

    public static Predicate<Expense> byDescription(String keyword) {
        return e -> e.description().toLowerCase().contains(keyword.toLowerCase());
    }
}
