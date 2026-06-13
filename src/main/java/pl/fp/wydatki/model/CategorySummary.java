package pl.fp.wydatki.model;

import java.math.BigDecimal;

/**
 * FP: wynik agregacji – czysty produkt transformacji Stream, nigdy nie jest modyfikowany.
 */
public record CategorySummary(
        Category category,
        BigDecimal total,
        long count,
        BigDecimal average
) {}
