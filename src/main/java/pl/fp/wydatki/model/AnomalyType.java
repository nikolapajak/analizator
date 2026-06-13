package pl.fp.wydatki.model;

/**
 * FP: sealed interface + permits gwarantuje zamknięty zbiór wariantów.
 * Każdy wariant jest rekordem (immutable) i niesie własne dane parametryczne.
 * label() eliminuje potrzebę switch po stronie wywołującego – polimorfizm zamiast
 * warunkowania, co jest bliższe idei pattern matching z programowania funkcyjnego.
 */
public sealed interface AnomalyType permits AnomalyType.StdDev, AnomalyType.IQR {

    String label();

    record StdDev(double sigmaMultiplier, double threshold) implements AnomalyType {
        public String label() {
            return String.format("StdDev (%.1fσ, próg=%.2f)", sigmaMultiplier, threshold);
        }
    }

    record IQR(double iqrMultiplier, double upperFence) implements AnomalyType {
        public String label() {
            return String.format("IQR (x%.1f, górny płot=%.2f)", iqrMultiplier, upperFence);
        }
    }
}
