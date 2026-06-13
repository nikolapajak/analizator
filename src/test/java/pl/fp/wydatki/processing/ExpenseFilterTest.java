package pl.fp.wydatki.processing;

import org.junit.jupiter.api.Test;
import pl.fp.wydatki.model.Category;
import pl.fp.wydatki.model.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy pokazują trzy właściwości FP czystych funkcji:
 *  1. Determinizm – ta sama funkcja + te same dane = zawsze ten sam wynik.
 *  2. Brak efektów ubocznych – lista wejściowa nie jest modyfikowana.
 *  3. Kompozycja predykatów – .and() / .or() tworzą nowe predykaty bez mutacji.
 */
class ExpenseFilterTest {

    private static final List<Expense> SAMPLE = List.of(
            new Expense(1, LocalDate.of(2024, 1, 10), new BigDecimal("50.00"),  Category.JEDZENIE,  "Lidl"),
            new Expense(2, LocalDate.of(2024, 2,  5), new BigDecimal("200.00"), Category.TRANSPORT, "PKP"),
            new Expense(3, LocalDate.of(2024, 3, 15), new BigDecimal("30.00"),  Category.JEDZENIE,  "Żabka"),
            new Expense(4, LocalDate.of(2024, 4, 20), new BigDecimal("500.00"), Category.ZAKUPY,    "Allegro"),
            new Expense(5, LocalDate.of(2024, 5,  1), new BigDecimal("80.00"),  Category.ROZRYWKA,  "Cinema")
    );

    @Test
    void filterByCategory_returnsOnlyMatchingCategory() {
        List<Expense> result = ExpenseFilter.filter(SAMPLE, Filters.byCategory(Category.JEDZENIE));
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(e -> e.category() == Category.JEDZENIE));
    }

    @Test
    void filterByDateRange_returnsExpensesInRange() {
        List<Expense> result = ExpenseFilter.filterByDateRange(
                SAMPLE, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 31));
        assertEquals(2, result.size());
    }

    @Test
    void filterByAmountRange_returnsExpensesInRange() {
        // Oczekiwane: 50.00 i 80.00 (30.00 za mało, 200.00 i 500.00 za dużo)
        List<Expense> result = ExpenseFilter.filterByAmountRange(
                SAMPLE, new BigDecimal("40.00"), new BigDecimal("100.00"));
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(e ->
                e.amount().compareTo(new BigDecimal("40.00")) >= 0 &&
                e.amount().compareTo(new BigDecimal("100.00")) <= 0));
    }

    @Test
    void filter_isDeterministic() {
        // Ta sama funkcja na tych samych danych musi zawsze dać identyczny wynik
        Predicate<Expense> pred = Filters.byCategory(Category.JEDZENIE);
        assertEquals(ExpenseFilter.filter(SAMPLE, pred), ExpenseFilter.filter(SAMPLE, pred));
    }

    @Test
    void filter_doesNotMutateInput() {
        List<Expense> originalCopy = List.copyOf(SAMPLE);
        ExpenseFilter.filter(SAMPLE, Filters.byMinAmount(new BigDecimal("100.00")));
        assertEquals(originalCopy, SAMPLE, "Lista wejściowa nie może zostać zmodyfikowana");
    }

    @Test
    void filter_resultIsUnmodifiable() {
        List<Expense> result = ExpenseFilter.filter(SAMPLE, Filters.byCategory(Category.JEDZENIE));
        assertThrows(UnsupportedOperationException.class,
                () -> result.add(SAMPLE.get(0)),
                "Stream.toList() zwraca listę niemodyfikowalną");
    }

    @Test
    void predicateComposition_andNarrowsResults() {
        // Kompozycja: kategoria JEDZENIE AND kwota >= 40
        Predicate<Expense> highFood = Filters.byCategory(Category.JEDZENIE)
                .and(Filters.byMinAmount(new BigDecimal("40.00")));
        List<Expense> result = ExpenseFilter.filter(SAMPLE, highFood);
        assertEquals(1, result.size());
        assertEquals(new BigDecimal("50.00"), result.get(0).amount());
    }

    @Test
    void predicateComposition_orBroadensResults() {
        // Kompozycja: JEDZENIE OR ZAKUPY
        Predicate<Expense> foodOrShopping = Filters.byCategory(Category.JEDZENIE)
                .or(Filters.byCategory(Category.ZAKUPY));
        List<Expense> result = ExpenseFilter.filter(SAMPLE, foodOrShopping);
        assertEquals(3, result.size());
    }

    @Test
    void filterByCategories_matchesAllSpecified() {
        List<Expense> result = ExpenseFilter.filterByCategories(
                SAMPLE, List.of(Category.JEDZENIE, Category.TRANSPORT));
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(e ->
                e.category() == Category.JEDZENIE || e.category() == Category.TRANSPORT));
    }

    @Test
    void filter_emptyInputReturnsEmpty() {
        List<Expense> result = ExpenseFilter.filter(List.of(), Filters.byCategory(Category.JEDZENIE));
        assertTrue(result.isEmpty());
    }
}
