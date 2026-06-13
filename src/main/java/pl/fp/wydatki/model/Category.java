package pl.fp.wydatki.model;

public enum Category {
    JEDZENIE, TRANSPORT, ROZRYWKA, RACHUNKI, ZDROWIE, ZAKUPY;

    public static Category fromString(String s) {
        return valueOf(s.trim().toUpperCase());
    }
}
