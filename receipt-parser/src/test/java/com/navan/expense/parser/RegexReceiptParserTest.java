package com.navan.expense.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegexReceiptParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonNode gold;
    private static JsonNode goldErrors;
    private final ReceiptParser parser = new ReceiptParserFactory().create(ReceiptParserKind.REGEX);

    @BeforeAll
    static void loadGold() throws IOException {
        gold = readJson("gold.json");
        goldErrors = readJson("gold-errors.json");
    }

    @ParameterizedTest
    @ValueSource(strings = {"receipt-clean", "receipt-tax-only", "receipt-mismatch", "receipt-big-amounts", "receipt-zero-amounts", "receipt-negative-amounts", "receipt-german"})
    void parsesValidFixturesToGold(String name) {
        ParsedReceipt parsed = parser.parse(readText(name + ".txt"));
        JsonNode expected = gold.get(name);

        assertThat(parsed.merchant()).isEqualTo(expected.get("merchant").asText());
        assertThat(parsed.date()).isEqualTo(LocalDate.parse(expected.get("date").asText()));
        assertThat(parsed.currency()).isEqualTo(expected.get("currency").asText());
        assertThat(parsed.grandTotal()).isEqualByComparingTo(expected.get("grand_total").decimalValue());
        assertThat(parsed.taxes()).hasSize(expected.get("taxes").size());
        for (int i = 0; i < parsed.taxes().size(); i++) {
            JsonNode tax = expected.get("taxes").get(i);
            assertThat(parsed.taxes().get(i).name()).isEqualTo(tax.get("name").asText());
            assertThat(parsed.taxes().get(i).rate()).isEqualByComparingTo(tax.get("rate").decimalValue());
            assertThat(parsed.taxes().get(i).amount()).isEqualByComparingTo(tax.get("amount").decimalValue());
        }
        assertThat(parsed.lineItems()).hasSize(expected.get("line_items").size());
        for (int i = 0; i < parsed.lineItems().size(); i++) {
            JsonNode item = expected.get("line_items").get(i);
            assertThat(parsed.lineItems().get(i).description()).isEqualTo(item.get("description").asText());
            assertThat(parsed.lineItems().get(i).amount()).isEqualByComparingTo(item.get("amount").decimalValue());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "receipt-garbled",
            "receipt-non-numeric",
            "receipt-unlabeled",
            "receipt-missing-merchant",
            "receipt-missing-date",
            "receipt-missing-currency",
            "receipt-missing-total"
    })
    void rejectsInvalidFixturesWithGoldErrors(String name) {
        JsonNode expected = goldErrors.get(name);
        assertThatThrownBy(() -> parser.parse(readText(name + ".txt")))
                .isInstanceOf(ReceiptParseException.class)
                .satisfies(ex -> {
                    ReceiptParseException parseEx = (ReceiptParseException) ex;
                    assertThat(parseEx.getFields()).hasSize(expected.get("fields").size());
                    for (JsonNode field : expected.get("fields")) {
                        assertThat(parseEx.getFields())
                                .anyMatch(actual -> actual.name().equals(field.get("name").asText())
                                        && actual.reason().equals(field.get("reason").asText()));
                    }
                });
    }

    @Test
    void emptyInputIsMissingRequiredFields() {
        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(ReceiptParseException.class)
                .satisfies(ex -> assertThat(((ReceiptParseException) ex).getFields())
                        .extracting(ReceiptParseException.FieldError::reason)
                        .containsOnly("MISSING"));
    }

    @Test
    void unlabeledValuesAreMissingNotUnparseable() {
        assertThatThrownBy(() -> parser.parse(readText("receipt-unlabeled.txt")))
                .isInstanceOf(ReceiptParseException.class)
                .satisfies(ex -> assertThat(((ReceiptParseException) ex).getFields())
                        .extracting(ReceiptParseException.FieldError::reason)
                        .containsOnly("MISSING"));
    }

    @Test
    void labelsOutsideWhitelistAreUnparseable() {
        assertThatThrownBy(() -> parser.parse("""
                VENDOR: Cafe Mitte
                WHEN: 2026-03-12
                MONEY: EUR
                Espresso                    3.50
                SUM                         3.50
                """))
                .isInstanceOf(ReceiptParseException.class)
                .satisfies(ex -> assertThat(((ReceiptParseException) ex).getFields())
                        .extracting(ReceiptParseException.FieldError::reason)
                        .containsOnly("UNPARSEABLE"));
    }

    @Test
    void parsesGermanCommaAmountsAndEuroSymbol() {
        ParsedReceipt commas = parser.parse(readText("receipt-german.txt"));
        assertThat(commas.currency()).isEqualTo("EUR");
        assertThat(commas.grandTotal()).isEqualByComparingTo("17.85");
        assertThat(commas.lineItems()).extracting(ParsedLineItem::amount)
                .containsExactly(new BigDecimal("3.50"), new BigDecimal("8.90"), new BigDecimal("2.60"));
        assertThat(commas.taxes().getFirst().amount()).isEqualByComparingTo("2.85");

        ParsedReceipt symbolAndThousands = parser.parse("""
                MERCHANT: Berliner Küche
                DATE: 2026-03-12
                CURRENCY: €
                Catering                    1.250,00
                Kaffee                         12,50 €
                TOTAL                       1.262,50 EUR
                """);
        assertThat(symbolAndThousands.currency()).isEqualTo("EUR");
        assertThat(symbolAndThousands.grandTotal()).isEqualByComparingTo("1262.50");
        assertThat(symbolAndThousands.lineItems()).extracting(ParsedLineItem::amount)
                .containsExactly(new BigDecimal("1250.00"), new BigDecimal("12.50"));
    }

    @Test
    void rejectsUnknownIsoCurrencyCode() {
        assertThatThrownBy(() -> parser.parse("""
                MERCHANT: Cafe Mitte
                DATE: 2026-03-12
                CURRENCY: FOO
                WATER                       4.00
                TOTAL                       4.00
                """))
                .isInstanceOf(ReceiptParseException.class)
                .satisfies(ex -> assertThat(((ReceiptParseException) ex).getFields())
                        .anyMatch(field -> field.name().equals("currency") && field.reason().equals("INVALID")));
    }

    @Test
    void allCapsMoneyLineIsStillALineItem() {
        ParsedReceipt parsed = parser.parse("""
                MERCHANT: Cafe Mitte
                DATE: 2026-03-12
                CURRENCY: EUR
                WATER                       4.00
                TOTAL                       4.00
                """);
        assertThat(parsed.lineItems()).extracting(ParsedLineItem::description).containsExactly("WATER");
        assertThat(parsed.grandTotal()).isEqualByComparingTo("4.00");
    }

    @Test
    void includesVatLineIsParsedAsTax() {
        ParsedReceipt parsed = parser.parse("""
                MERCHANT: Berlin Taxi GmbH
                DATE: 2026-03-12
                CURRENCY: EUR
                Trip fare
                TOTAL                      24.00
                incl. VAT 19%               3.83
                """);
        assertThat(parsed.lineItems()).isEmpty();
        assertThat(parsed.taxes()).containsExactly(new ParsedTax("VAT", new BigDecimal("0.1900"), new BigDecimal("3.83")));
        assertThat(parsed.grandTotal()).isEqualByComparingTo("24.00");
    }

    @Test
    void parsesHundredItemsWhenSubtotalIsValid() {
        ParsedReceipt parsed = parser.parse(readText("receipt-100-items.txt"));
        JsonNode expected = gold.get("receipt-100-items");
        assertHundredItemHeader(parsed, expected);
        assertThat(parsed.lineItems()).hasSize(100);
        assertThat(parsed.lineItems()).noneMatch(item -> item.description().toLowerCase().contains("subtotal"));
        assertThat(parsed.lineItems().getFirst().description()).isEqualTo("Item 001");
        assertThat(parsed.lineItems().getLast().description()).isEqualTo("Item 100");
        BigDecimal itemSum = parsed.lineItems().stream().map(ParsedLineItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(itemSum).isEqualByComparingTo("100.00");
        assertThat(itemSum.add(parsed.taxes().getFirst().amount())).isEqualByComparingTo(parsed.grandTotal());
    }

    @Test
    void parsesHundredItemsWhenSubtotalDoesNotMatch() {
        ParsedReceipt parsed = parser.parse(readText("receipt-100-mismatch.txt"));
        JsonNode expected = gold.get("receipt-100-mismatch");
        assertHundredItemHeader(parsed, expected);
        assertThat(parsed.lineItems()).hasSize(100);
        assertThat(parsed.lineItems()).noneMatch(item -> item.description().toLowerCase().contains("subtotal"));
        BigDecimal itemSum = parsed.lineItems().stream().map(ParsedLineItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(itemSum).isEqualByComparingTo("100.00");
        assertThat(itemSum.add(parsed.taxes().getFirst().amount())).isNotEqualByComparingTo(parsed.grandTotal());
        assertThat(parsed.grandTotal()).isEqualByComparingTo("250.00");
    }

    @Test
    void parsesSignedAndLargeMoneyValues() {
        ParsedReceipt zeros = parser.parse(readText("receipt-zero-amounts.txt"));
        assertThat(zeros.grandTotal()).isEqualByComparingTo("0.00");
        assertThat(zeros.lineItems()).extracting(ParsedLineItem::amount).containsOnly(new BigDecimal("0.00"));

        ParsedReceipt negatives = parser.parse(readText("receipt-negative-amounts.txt"));
        assertThat(negatives.grandTotal()).isEqualByComparingTo("-7.14");
        assertThat(negatives.lineItems()).extracting(ParsedLineItem::amount)
                .containsExactly(new BigDecimal("-5.00"), new BigDecimal("-1.00"));
        assertThat(negatives.taxes().getFirst().amount()).isEqualByComparingTo("-1.14");

        ParsedReceipt big = parser.parse(readText("receipt-big-amounts.txt"));
        assertThat(big.grandTotal()).isEqualByComparingTo("4165000000.00");
        assertThat(big.lineItems()).extracting(ParsedLineItem::amount)
                .containsExactly(new BigDecimal("1000000000.00"), new BigDecimal("2000000000.00"), new BigDecimal("500000000.00"));
    }

    @Test
    void doesNotInventBalancingLineOnMismatch() {
        ParsedReceipt parsed = parser.parse(readText("receipt-mismatch.txt"));
        BigDecimal itemSum = parsed.lineItems().stream()
                .map(ParsedLineItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(itemSum.add(parsed.taxes().getFirst().amount())).isNotEqualByComparingTo(parsed.grandTotal());
        assertThat(parsed.lineItems()).extracting(ParsedLineItem::description).isEqualTo(List.of("Water", "Snacks"));
    }

    private static void assertHundredItemHeader(ParsedReceipt parsed, JsonNode expected) {
        assertThat(parsed.merchant()).isEqualTo(expected.get("merchant").asText());
        assertThat(parsed.date()).isEqualTo(LocalDate.parse(expected.get("date").asText()));
        assertThat(parsed.currency()).isEqualTo(expected.get("currency").asText());
        assertThat(parsed.grandTotal()).isEqualByComparingTo(expected.get("grand_total").decimalValue());
        assertThat(parsed.taxes()).hasSize(1);
        assertThat(parsed.taxes().getFirst().name()).isEqualTo("VAT");
        assertThat(parsed.taxes().getFirst().amount()).isEqualByComparingTo(expected.get("taxes").get(0).get("amount").decimalValue());
    }

    private static JsonNode readJson(String name) throws IOException {
        try (InputStream in = resource(name)) {
            return MAPPER.readTree(in);
        }
    }

    private static String readText(String name) {
        try (InputStream in = resource(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static InputStream resource(String name) {
        return Objects.requireNonNull(RegexReceiptParserTest.class.getClassLoader().getResourceAsStream(name), name);
    }
}
