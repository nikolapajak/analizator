package pl.fp.wydatki.model;

/**
 * FP: rekord agregujący wykryty wydatek i kontekst anomalii.
 * deviation = procentowe odchylenie kwoty od średniej kategorii (zawsze >= 0 dla anomalii).
 */
public record Anomaly(
        Expense expense,
        AnomalyType type,
        double categoryMean,
        double deviation
) {}
