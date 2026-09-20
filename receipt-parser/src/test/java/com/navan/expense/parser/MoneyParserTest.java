package com.navan.expense.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyParserTest {

    private final MoneyParser parser = new MoneyParser();

    @ParameterizedTest
    @CsvSource({
            "17.85, 17.85",
            "'17,85', 17.85",
            "'1.234,56', 1234.56",
            "'1,234.56', 1234.56",
            "'-1,14', -1.14",
            "'1.000.000,00', 1000000.00",
            "4165000000.00, 4165000000.00",
            "'3,50 €', 3.50",
            "'EUR 17,85', 17.85",
            "'17,85 EUR', 17.85"
    })
    void parsesDotAndCommaAmounts(String raw, String expected) {
        assertThat(parser.parseAmount(raw)).contains(new BigDecimal(expected));
    }

    @Test
    void rejectsNonNumericAmount() {
        assertThat(parser.parseAmount("N/A")).isEmpty();
        assertThat(parser.parseAmount("abc")).isEmpty();
    }

    @Test
    void parsesIsoCodesWithJavaCurrency() {
        assertThat(parser.parseCurrency("EUR")).contains("EUR");
        assertThat(parser.parseCurrency("eur")).contains("EUR");
        assertThat(parser.parseCurrency("USD")).contains(Currency.getInstance("USD").getCurrencyCode());
        assertThat(parser.parseCurrency("FOO")).isEmpty();
        assertThat(parser.parseCurrency("EU")).isEmpty();
    }

    @Test
    void parsesEuroSymbolUsingJavaCurrency() {
        String euro = Currency.getInstance("EUR").getSymbol(Locale.GERMANY);
        assertThat(parser.parseCurrency(euro)).contains("EUR");
    }
}
