package pl.fp.wydatki.model;

import java.math.BigDecimal;

/**
 * FP: jeden punkt na wykresie trendu.
 * periodLabel: "2024-03" (miesiąc) lub "2024-W12" (tydzień ISO) lub "2024-03-15" (dzień).
 */
public record TrendPoint(
        String periodLabel,
        BigDecimal total,
        long count
) {}
