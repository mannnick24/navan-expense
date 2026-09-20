package com.navan.expense.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class MoneyParser {

    private static final List<Currency> WELL_KNOWN = List.of(
            Currency.getInstance("EUR"),
            Currency.getInstance("USD"),
            Currency.getInstance("GBP"),
            Currency.getInstance("CHF"),
            Currency.getInstance("JPY")
    );
    private static final List<Locale> SYMBOL_LOCALES = List.of(Locale.GERMANY, Locale.US, Locale.UK, Locale.ROOT);
    private static final Pattern ISO_CODE = Pattern.compile("^[A-Za-z]{3}$");
    private static final Pattern CURRENCY_TOKEN = Pattern.compile("(?i)(?:[A-Z]{3}|€|\\$|£)");
    private static final Pattern TRAILING_CURRENCY = Pattern.compile("(?i)\\s*" + CURRENCY_TOKEN.pattern() + "$");
    private static final Pattern LEADING_CURRENCY = Pattern.compile("(?i)^" + CURRENCY_TOKEN.pattern() + "\\s*");
    private static final Pattern AMOUNT_SHAPE = Pattern.compile("^-?\\d+(?:[.,]\\d{3})*(?:[.,]\\d{2})$");

    public Optional<String> parseCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (ISO_CODE.matcher(value).matches()) {
            try {
                return Optional.of(Currency.getInstance(value.toUpperCase(Locale.ROOT)).getCurrencyCode());
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }
        for (Currency currency : WELL_KNOWN) {
            for (Locale locale : SYMBOL_LOCALES) {
                if (value.equals(currency.getSymbol(locale))) {
                    return Optional.of(currency.getCurrencyCode());
                }
            }
        }
        return Optional.empty();
    }

    public Optional<BigDecimal> parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = stripCurrencyTokens(raw.trim());
        value = value.replace('\u00A0', ' ').replace(" ", "");
        if (!AMOUNT_SHAPE.matcher(value).matches()) {
            return Optional.empty();
        }
        String normalized = normalizeSeparators(value);
        try {
            return Optional.of(new BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY));
        } catch (NumberFormatException | ArithmeticException ex) {
            return Optional.empty();
        }
    }

    String stripTrailingCurrency(String line) {
        return TRAILING_CURRENCY.matcher(line.trim()).replaceFirst("").trim();
    }

    private static String stripCurrencyTokens(String raw) {
        String value = LEADING_CURRENCY.matcher(raw).replaceFirst("");
        return TRAILING_CURRENCY.matcher(value).replaceFirst("").trim();
    }

    private static String normalizeSeparators(String value) {
        int lastComma = value.lastIndexOf(',');
        int lastDot = value.lastIndexOf('.');
        if (lastComma > lastDot) {
            return value.replace(".", "").replace(',', '.');
        }
        return value.replace(",", "");
    }
}
