package pl.fp.wydatki.processing;

import pl.fp.wydatki.model.Category;
import pl.fp.wydatki.model.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

/**
 * FP: czyste funkcje filtrowania – (List<Expense>, Predicate) → List<Expense>.
 *
 * Kluczowe właściwości funkcyjne:
 *  1. Brak efektów ubocznych – lista wejściowa nie jest modyfikowana.
 *  2. Determinizm – te same argumenty zawsze dają ten sam wynik.
 *  3. Stream.toList() zwraca listę niemodyfikowalną (Java 16+).
 *  4. Filtrowanie wielu kategorii używa fold po predykatach (reduce + or),
 *     co ilustruje kompozycję funkcji bez jawnych pętli.
 */
public final class ExpenseFilter {
    private ExpenseFilter() {}

    public static List<Expense> filter(List<Expense> expenses, Predicate<Expense> predicate) {
        return expenses.stream()
                .filter(predicate)
                .toList();
    }

    public static List<Expense> filterByCategories(List<Expense> expenses,
                                                    List<Category> categories) {
        // FP: fold – zredukuj listę kategorii do jednego predykatu przez OR
        // reduce(identityFalse, or) = cat1.or(cat2).or(cat3)...
        Predicate<Expense> combined = categories.stream()
                .map(Filters::byCategory)
                .reduce(__ -> false, (a, b) -> a.or(b));
        return filter(expenses, combined);
    }

    public static List<Expense> filterByDateRange(List<Expense> expenses,
                                                   LocalDate from, LocalDate to) {
        return filter(expenses, Filters.byDateRange(from, to));
    }

    public static List<Expense> filterByAmountRange(List<Expense> expenses,
                                                     BigDecimal min, BigDecimal max) {
        return filter(expenses, Filters.byAmountRange(min, max));
    }
}
