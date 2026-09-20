package com.navan.expense.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptApiContractE2ETest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ReceiptApiClient api;
    private JsonNode gold;
    private JsonNode goldErrors;

    @BeforeEach
    void setUp() throws Exception {
        api = new ReceiptApiClient(RestTestClient.bindToServer().baseUrl(baseUrl()).build());
        gold = MAPPER.readTree(getClass().getClassLoader().getResourceAsStream("gold.json"));
        goldErrors = MAPPER.readTree(getClass().getClassLoader().getResourceAsStream("gold-errors.json"));
    }

    private static String baseUrl() {
        String fromProperty = System.getProperty("e2e.baseUrl");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        String fromEnv = System.getenv("E2E_BASE_URL");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return "http://127.0.0.1:8080";
    }

    @Test
    void healthIsUp() {
        assertThat(api.health().get("status")).isEqualTo("UP");
    }

    @Test
    void prometheusScrapeIsExposed() {
        String body = api.prometheus();
        assertThat(body).contains("jvm_");
    }

    @ParameterizedTest
    @ValueSource(strings = {"receipt-clean", "receipt-tax-only", "receipt-mismatch"})
    void processMatchesGold(String name) {
        UUID receiptId = api.upload(name + ".png", ReceiptApiClient.utf8("image"));
        Map<?, ?> tx = api.process(receiptId).expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        JsonNode expected = gold.get(name);
        assertThat(tx.get("merchant")).isEqualTo(expected.get("merchant").asText());
        assertThat(tx.get("currency")).isEqualTo(expected.get("currency").asText());
        assertThat(tx.get("itemize_status")).isEqualTo(expected.get("itemize_status").asText());
        assertThat(new BigDecimal(tx.get("grand_total").toString())).isEqualByComparingTo(expected.get("grand_total").decimalValue());
        assertThat((Iterable<?>) tx.get("line_items")).hasSize(expected.get("line_items").size());
        Object txId = tx.get("transaction_id");
        Map<?, ?> fetched = api.getTransaction(txId.toString()).expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(fetched.get("transaction_id")).isEqualTo(txId);
        Map<?, ?> itemized = api.itemize(txId.toString()).expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(itemized.get("transaction_id")).isEqualTo(txId);
    }

    @Test
    void hundredItemsWithValidSubtotalAreComplete() {
        UUID receiptId = api.upload("receipt-100-items.png", ReceiptApiClient.utf8("image"));
        Map<?, ?> tx = api.process(receiptId).expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        JsonNode expected = gold.get("receipt-100-items");
        assertThat(tx.get("merchant")).isEqualTo(expected.get("merchant").asText());
        assertThat(tx.get("itemize_status")).isEqualTo("COMPLETE");
        assertThat(new BigDecimal(tx.get("grand_total").toString())).isEqualByComparingTo("119.00");
        assertThat((Iterable<?>) tx.get("line_items")).hasSize(100);
    }

    @Test
    void hundredItemsWithInvalidSubtotalNeedReviewAndKeepAllItems() {
        UUID receiptId = api.upload("receipt-100-mismatch.png", ReceiptApiClient.utf8("image"));
        Map<?, ?> tx = api.process(receiptId).expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        JsonNode expected = gold.get("receipt-100-mismatch");
        assertThat(tx.get("merchant")).isEqualTo(expected.get("merchant").asText());
        assertThat(tx.get("itemize_status")).isEqualTo("NEEDS_REVIEW");
        assertThat(new BigDecimal(tx.get("grand_total").toString())).isEqualByComparingTo("250.00");
        assertThat((Iterable<?>) tx.get("line_items")).hasSize(100);
    }

    @Test
    void largeAmountsStayCompleteWhenTheyReconcile() {
        UUID receiptId = api.upload("receipt-big-amounts.png", ReceiptApiClient.utf8("image"));
        Map<?, ?> tx = api.process(receiptId).expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(tx.get("itemize_status")).isEqualTo("COMPLETE");
        assertThat(new BigDecimal(tx.get("grand_total").toString())).isEqualByComparingTo("4165000000.00");
        assertThat((Iterable<?>) tx.get("line_items")).hasSize(3);
    }

    @Test
    void zeroAmountsNeedReview() {
        UUID receiptId = api.upload("receipt-zero-amounts.png", ReceiptApiClient.utf8("image"));
        Map<?, ?> tx = api.process(receiptId).expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(tx.get("itemize_status")).isEqualTo("NEEDS_REVIEW");
        assertThat(new BigDecimal(tx.get("grand_total").toString())).isEqualByComparingTo("0.00");
        assertThat((Iterable<?>) tx.get("line_items")).hasSize(2);
    }

    @Test
    void negativeAmountsNeedReview() {
        UUID receiptId = api.upload("receipt-negative-amounts.png", ReceiptApiClient.utf8("image"));
        Map<?, ?> tx = api.process(receiptId).expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(tx.get("itemize_status")).isEqualTo("NEEDS_REVIEW");
        assertThat(new BigDecimal(tx.get("grand_total").toString())).isEqualByComparingTo("-7.14");
        assertThat((Iterable<?>) tx.get("line_items")).hasSize(2);
    }

    @Test
    void mismatchMustNotInventBalancingLine() {
        UUID receiptId = api.upload("receipt-mismatch.pdf", ReceiptApiClient.utf8("image"));
        Map<?, ?> tx = api.process(receiptId).expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(tx.get("itemize_status")).isEqualTo("NEEDS_REVIEW");
        assertThat(new BigDecimal(tx.get("grand_total").toString())).isEqualByComparingTo("18.50");
        assertThat((Iterable<?>) tx.get("line_items")).hasSize(2);
    }

    @Test
    void patchValidItemsOnCleanReceiptSucceeds() {
        UUID receiptId = api.upload("receipt-clean.png", ReceiptApiClient.utf8("image"));
        Map<?, ?> tx = api.process(receiptId).expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        api.patchItems(tx.get("transaction_id").toString(), """
                {"items":[
                  {"description":"Espresso","amount":3.50},
                  {"description":"Sandwich","amount":8.90},
                  {"description":"Mineral water","amount":2.60}
                ]}
                """).expectStatus().isOk();
    }

    @Test
    void patchMismatchItemsReturns409() {
        UUID receiptId = api.upload("receipt-clean.png", ReceiptApiClient.utf8("image"));
        Map<?, ?> tx = api.process(receiptId).expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        api.patchItems(tx.get("transaction_id").toString(), """
                {"items":[{"description":"Water","amount":4.00},{"description":"Snacks","amount":6.00}]}
                """).expectStatus().isEqualTo(409)
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("error")).isEqualTo("ITEMIZATION_MISMATCH"));
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
    void invalidFixturesReturn400AndDoNotCreateTransaction(String name) {
        UUID receiptId = api.upload(name + ".png", ReceiptApiClient.utf8("image"));
        Map<?, ?> body = api.process(receiptId).expectStatus().isBadRequest().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body.get("error")).isEqualTo("RECEIPT_PARSE_FAILED");
        JsonNode expectedFields = goldErrors.get(name).get("fields");
        assertThat((Iterable<?>) body.get("fields")).hasSize(expectedFields.size());
        api.getTransaction(UUID.randomUUID().toString()).expectStatus().isNotFound();
    }

    @Test
    void unknownFilenameReturnsOcrNotFound() {
        UUID receiptId = api.upload("unknown.png", ReceiptApiClient.utf8("image"));
        api.process(receiptId).expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("error")).isEqualTo("OCR_TEXT_NOT_FOUND"));
    }

    @Test
    void uploadRejectsNonImageOrMismatchedMagic() {
        api.uploadExpectingError("notes.txt", MediaType.TEXT_PLAIN, ReceiptApiClient.utf8("hello"))
                .expectStatus().isEqualTo(415)
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("error")).isEqualTo("UNSUPPORTED_RECEIPT_TYPE"));
        api.uploadExpectingError("receipt-clean.png", MediaType.IMAGE_PNG, ReceiptApiClient.utf8("this is not a png"))
                .expectStatus().isEqualTo(415)
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("error")).isEqualTo("UNSUPPORTED_RECEIPT_TYPE"));
    }

    @Test
    void itemizeAndPatchInvalidIdsReturn400() {
        api.itemize("not-a-uuid").expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("error")).isEqualTo("INVALID_TRANSACTION_ID"));
        api.itemize(UUID.randomUUID().toString()).expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("error")).isEqualTo("INVALID_TRANSACTION_ID"));
        api.patchItems("not-a-uuid", """
                {"items":[{"description":"Water","amount":4.00}]}
                """).expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("error")).isEqualTo("INVALID_TRANSACTION_ID"));
        api.patchItems(UUID.randomUUID().toString(), """
                {"items":[{"description":"Water","amount":4.00}]}
                """).expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("error")).isEqualTo("INVALID_TRANSACTION_ID"));
    }
}
