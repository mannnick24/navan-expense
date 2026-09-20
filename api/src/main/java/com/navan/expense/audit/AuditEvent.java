package com.navan.expense.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        Instant timestamp,
        String httpMethod,
        String path,
        String query,
        Map<String, String> pathVariables,
        int status,
        long durationMs,
        String outcome,
        String error,
        UUID receiptId,
        UUID transactionId,
        String uploadFilename,
        String uploadContentType,
        Long uploadSize
) {
}
