package com.navan.expense.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSlf4jAuditPublisherTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    private final JsonSlf4jAuditPublisher publisher = new JsonSlf4jAuditPublisher(jsonMapper);
    private ListAppender<ILoggingEvent> appender;
    private Logger auditLogger;

    @BeforeEach
    void attachAppender() {
        auditLogger = (Logger) LoggerFactory.getLogger("audit");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void writesOneSnakeCaseJsonLine() {
        UUID receiptId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AuditEvent event = new AuditEvent(
                Instant.parse("2026-03-12T10:15:30Z"),
                "POST",
                "/receipts/" + receiptId + "/process",
                null,
                Map.of("id", receiptId.toString()),
                200,
                12L,
                "SUCCESS",
                null,
                receiptId,
                null,
                "receipt-clean.png",
                "image/png",
                128L
        );

        publisher.publish(event);

        assertThat(appender.list).hasSize(1);
        String json = appender.list.getFirst().getFormattedMessage();
        assertThat(json).doesNotContain("\n");
        JsonNode node = jsonMapper.readTree(json);
        assertThat(node.get("http_method").asString()).isEqualTo("POST");
        assertThat(node.get("path").asString()).isEqualTo("/receipts/" + receiptId + "/process");
        assertThat(node.get("status").asInt()).isEqualTo(200);
        assertThat(node.get("duration_ms").asLong()).isEqualTo(12L);
        assertThat(node.get("outcome").asString()).isEqualTo("SUCCESS");
        assertThat(node.get("receipt_id").asString()).isEqualTo(receiptId.toString());
        assertThat(node.get("upload_filename").asString()).isEqualTo("receipt-clean.png");
        assertThat(node.get("upload_content_type").asString()).isEqualTo("image/png");
        assertThat(node.get("upload_size").asLong()).isEqualTo(128L);
        assertThat(node.has("httpMethod")).isFalse();
    }

    @Test
    void errorEventDoesNotIncludeOcrOrFileBytes() {
        AuditEvent event = new AuditEvent(
                Instant.parse("2026-03-12T10:15:30Z"),
                "POST",
                "/receipts/11111111-1111-1111-1111-111111111111/process",
                null,
                Map.of(),
                400,
                5L,
                "ERROR",
                "RECEIPT_PARSE_FAILED",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                null,
                "receipt-garbled.png",
                "image/png",
                40L
        );

        publisher.publish(event);

        String json = appender.list.getFirst().getFormattedMessage();
        assertThat(json).doesNotContain("MERCH#NT");
        assertThat(json).doesNotContain("raw_ocr");
        assertThat(json).doesNotContain("secret-bytes");
        JsonNode node = jsonMapper.readTree(json);
        assertThat(node.get("error").asString()).isEqualTo("RECEIPT_PARSE_FAILED");
        assertThat(node.get("outcome").asString()).isEqualTo("ERROR");
        assertThat(node.get("status").asInt()).isEqualTo(400);
    }
}
