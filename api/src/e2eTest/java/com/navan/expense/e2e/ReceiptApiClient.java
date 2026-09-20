package com.navan.expense.e2e;

import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

final class ReceiptApiClient {

    private final RestTestClient client;

    ReceiptApiClient(RestTestClient client) {
        this.client = client;
    }

    Map<?, ?> health() {
        return client.get().uri("/health").exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
    }

    String prometheus() {
        return client.get().uri("/actuator/prometheus").exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
    }

    UUID upload(String filename, byte[] bytes) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new NamedBytes(filename, bytes));
        Map<?, ?> body = client.post()
                .uri("/receipts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        return UUID.fromString(String.valueOf(body.get("receipt_id")));
    }

    RestTestClient.ResponseSpec process(UUID receiptId) {
        return client.post().uri("/receipts/{id}/process", receiptId).exchange();
    }

    RestTestClient.ResponseSpec getTransaction(String id) {
        return client.get().uri("/transactions/{id}", id).exchange();
    }

    RestTestClient.ResponseSpec itemize(String id) {
        return client.post().uri("/transactions/{id}/itemize", id).exchange();
    }

    RestTestClient.ResponseSpec patchItems(String id, String json) {
        return client.patch().uri("/transactions/{id}/items", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
    }

    private static final class NamedBytes extends ByteArrayResource {
        private final String filename;

        private NamedBytes(String filename, byte[] bytes) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
