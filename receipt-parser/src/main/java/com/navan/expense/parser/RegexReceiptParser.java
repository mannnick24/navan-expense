package com.navan.expense.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegexReceiptParser implements ReceiptParser {

    private static final Set<String> KNOWN_LABELS = Set.of("MERCHANT", "DATE", "CURRENCY", "TOTAL", "SUBTOTAL");

    private static final Pattern COLON_LABEL = Pattern.compile("^([^\\s:]+)\\s*:\\s*(.*)$");
    private static final Pattern CAPS_TOKEN_VALUE = Pattern.compile("^([A-Z][A-Z0-9#]*)\\s+(.*)$");
    private static final Pattern TAX = Pattern.compile(
            "^(?:incl\\.\\s+)?(VAT|GST|TAX)\\s+(\\d+(?:\\.\\d+)?)%\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SUBTOTAL = Pattern.compile("^Subtotal\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAREN_NOTE = Pattern.compile("^\\(.*\\)$");

    private final MoneyParser money = new MoneyParser();

    @Override
    public ParsedReceipt parse(String rawText) {
        List<ReceiptParseException.FieldError> errors = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            throw missingAll("MISSING");
        }

        String merchant = null;
        LocalDate date = null;
        String currency = null;
        BigDecimal grandTotal = null;
        boolean sawMerchant = false;
        boolean sawDate = false;
        boolean sawCurrency = false;
        boolean sawTotal = false;
        boolean sawUnrecognizedLabel = false;
        List<ParsedTax> taxes = new ArrayList<>();
        List<ParsedLineItem> items = new ArrayList<>();

        for (String rawLine : rawText.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || PAREN_NOTE.matcher(line).matches()) {
                continue;
            }

            if (SUBTOTAL.matcher(line).find()) {
                continue;
            }

            Matcher tax = TAX.matcher(line);
            if (tax.matches()) {
                Optional<BigDecimal> amount = money.parseAmount(tax.group(3).trim());
                if (amount.isPresent()) {
                    BigDecimal rate = new BigDecimal(tax.group(2)).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                    taxes.add(new ParsedTax(tax.group(1).toUpperCase(Locale.ROOT), rate, amount.get()));
                }
                continue;
            }

            Optional<LabelAttempt> attempt = readLabelAttempt(line);
            if (attempt.isPresent()) {
                LabelAttempt label = attempt.get();
                if (!label.known()) {
                    sawUnrecognizedLabel = true;
                    continue;
                }
                switch (label.key()) {
                    case "MERCHANT" -> {
                        sawMerchant = true;
                        if (label.value().isEmpty()) {
                            errors.add(new ReceiptParseException.FieldError("merchant", "MISSING"));
                        } else {
                            merchant = label.value();
                        }
                    }
                    case "DATE" -> {
                        sawDate = true;
                        Optional<LocalDate> parsed = parseDate(label.value());
                        if (parsed.isPresent()) {
                            date = parsed.get();
                        } else {
                            errors.add(new ReceiptParseException.FieldError("date", "INVALID"));
                        }
                    }
                    case "CURRENCY" -> {
                        sawCurrency = true;
                        Optional<String> parsed = money.parseCurrency(label.value());
                        if (parsed.isPresent()) {
                            currency = parsed.get();
                        } else if (label.value().isEmpty()) {
                            errors.add(new ReceiptParseException.FieldError("currency", "MISSING"));
                        } else {
                            errors.add(new ReceiptParseException.FieldError("currency", "INVALID"));
                        }
                    }
                    case "TOTAL" -> {
                        sawTotal = true;
                        Optional<BigDecimal> amount = money.parseAmount(label.value());
                        if (amount.isPresent()) {
                            grandTotal = amount.get();
                        } else {
                            errors.add(new ReceiptParseException.FieldError("grand_total", "NON_NUMERIC"));
                        }
                    }
                    default -> {
                    }
                }
                continue;
            }

            parseLineItem(line).ifPresent(items::add);
        }

        String absentReason = sawUnrecognizedLabel ? "UNPARSEABLE" : "MISSING";
        if (!sawMerchant) {
            errors.add(new ReceiptParseException.FieldError("merchant", absentReason));
        }
        if (!sawDate) {
            errors.add(new ReceiptParseException.FieldError("date", absentReason));
        }
        if (!sawCurrency) {
            errors.add(new ReceiptParseException.FieldError("currency", absentReason));
        }
        if (!sawTotal) {
            errors.add(new ReceiptParseException.FieldError("grand_total", absentReason));
        }

        if (!errors.isEmpty() || merchant == null || date == null || currency == null || grandTotal == null) {
            if (errors.isEmpty()) {
                throw missingAll(absentReason);
            }
            throw new ReceiptParseException(errors);
        }

        return new ParsedReceipt(merchant, date, currency, grandTotal, List.copyOf(taxes), List.copyOf(items));
    }

    private Optional<LabelAttempt> readLabelAttempt(String line) {
        Matcher colon = COLON_LABEL.matcher(line);
        if (colon.matches()) {
            return Optional.of(new LabelAttempt(colon.group(1).toUpperCase(Locale.ROOT), colon.group(2).trim()));
        }
        Matcher caps = CAPS_TOKEN_VALUE.matcher(line);
        if (caps.matches()) {
            String key = caps.group(1).toUpperCase(Locale.ROOT);
            String value = caps.group(2).trim();
            if (KNOWN_LABELS.contains(key) || money.parseAmount(value).isEmpty()) {
                return Optional.of(new LabelAttempt(key, value));
            }
        }
        return Optional.empty();
    }

    private Optional<ParsedLineItem> parseLineItem(String line) {
        String withoutCurrency = money.stripTrailingCurrency(line);
        int breakAt = withoutCurrency.lastIndexOf(' ');
        if (breakAt <= 0) {
            return Optional.empty();
        }
        String description = withoutCurrency.substring(0, breakAt).trim();
        Optional<BigDecimal> amount = money.parseAmount(withoutCurrency.substring(breakAt + 1).trim());
        if (amount.isEmpty() || description.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedLineItem(description, amount.get(), null, null));
    }

    private static ReceiptParseException missingAll(String reason) {
        return new ReceiptParseException(List.of(
                new ReceiptParseException.FieldError("merchant", reason),
                new ReceiptParseException.FieldError("date", reason),
                new ReceiptParseException.FieldError("currency", reason),
                new ReceiptParseException.FieldError("grand_total", reason)
        ));
    }

    private static Optional<LocalDate> parseDate(String value) {
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private record LabelAttempt(String key, String value) {
        boolean known() {
            return KNOWN_LABELS.contains(key);
        }
    }
}
