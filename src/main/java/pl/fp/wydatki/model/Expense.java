package pl.fp.wydatki.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * FP: record = niemutowalna klasa danych.
 * Kompilator automatycznie generuje equals/hashCode/toString oparte na polach –
 * nie ma żadnych setterów, więc obiekt jest z definicji thread-safe i value-type.
 */
public record Expense(
        int id,
        LocalDate date,
        BigDecimal amount,
        Category category,
        String description
) {}
