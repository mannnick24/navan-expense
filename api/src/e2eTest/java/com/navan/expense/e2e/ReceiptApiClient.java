package com.navan.expense.e2e;

import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
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
        byte[] body = withMagic(filename, bytes);
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(mediaType(filename));
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new HttpEntity<>(new NamedBytes(filename, body), fileHeaders));
        Map<?, ?> bodyJson = client.post()
                .uri("/receipts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        return UUID.fromString(String.valueOf(bodyJson.get("receipt_id")));
    }

    RestTestClient.ResponseSpec uploadExpectingError(String filename, MediaType contentType, byte[] bytes) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(contentType);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new HttpEntity<>(new NamedBytes(filename, bytes), fileHeaders));
        return client.post()
                .uri("/receipts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .exchange();
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

    private static MediaType mediaType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.IMAGE_PNG;
    }

    private static byte[] withMagic(String filename, byte[] payload) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return startsWith(payload, new byte[]{'%', 'P', 'D', 'F'}) ? payload : concat(new byte[]{'%', 'P', 'D', 'F', '-'}, payload);
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
            return startsWith(payload, jpeg) ? payload : concat(jpeg, payload);
        }
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        return startsWith(payload, png) ? payload : concat(png, payload);
    }

    private static boolean startsWith(byte[] haystack, byte[] needle) {
        if (haystack.length < needle.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(haystack, 0, needle.length), needle);
    }

    private static byte[] concat(byte[] prefix, byte[] rest) {
        byte[] out = Arrays.copyOf(prefix, prefix.length + rest.length);
        System.arraycopy(rest, 0, out, prefix.length, rest.length);
        return out;
    }
}
