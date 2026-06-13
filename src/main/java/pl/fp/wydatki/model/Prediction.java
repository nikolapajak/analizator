package pl.fp.wydatki.model;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * FP: wynik predykcji – niemutowalny, niesie własną miarę pewności.
 * confidence: R² dla regresji liniowej, 1-CV dla EMA (zakres [0, 1]).
 */
public record Prediction(
        YearMonth month,
        BigDecimal predictedAmount,
        double confidence
) {}
