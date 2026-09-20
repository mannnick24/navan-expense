package com.navan.expense.audit;

import com.navan.expense.api.error.InvalidTransactionIdException;
import com.navan.expense.api.error.ItemizationMismatchException;
import com.navan.expense.api.error.OcrTextNotFoundException;
import com.navan.expense.api.error.ReceiptNotFoundException;
import com.navan.expense.api.error.TransactionNotFoundException;
import com.navan.expense.parser.ReceiptParseException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
public class ApiAuditAspect {

    private final AuditPublisher publisher;

    public ApiAuditAspect(AuditPublisher publisher) {
        this.publisher = publisher;
    }

    @Around("within(@org.springframework.web.bind.annotation.RestController *) && !within(com.navan.expense.health.HealthController)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        long started = System.nanoTime();
        HttpServletRequest request = currentRequest();
        try {
            Object result = joinPoint.proceed();
            int status = currentStatus(200);
            publisher.publish(event(request, joinPoint, status, started, "SUCCESS", null));
            return result;
        } catch (Throwable ex) {
            publisher.publish(event(request, joinPoint, statusOf(ex), started, "ERROR", errorCode(ex)));
            throw ex;
        }
    }

    private AuditEvent event(
            HttpServletRequest request,
            ProceedingJoinPoint joinPoint,
            int status,
            long started,
            String outcome,
            String error
    ) {
        Map<String, String> pathVariables = pathVariables(request);
        UploadMeta upload = uploadMeta(joinPoint.getArgs());
        String path = request == null ? null : request.getRequestURI();
        UUID pathId = uuidFrom(pathVariables.get("id"));
        boolean receiptPath = path != null && path.contains("/receipts/");
        boolean transactionPath = path != null && path.contains("/transactions/");
        return new AuditEvent(
                Instant.now(),
                request == null ? null : request.getMethod(),
                path,
                request == null ? null : request.getQueryString(),
                pathVariables,
                status,
                (System.nanoTime() - started) / 1_000_000,
                outcome,
                error,
                receiptPath ? pathId : null,
                transactionPath ? pathId : null,
                upload.filename(),
                upload.contentType(),
                upload.size()
        );
    }

    private static UploadMeta uploadMeta(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof MultipartFile file) {
                return new UploadMeta(file.getOriginalFilename(), file.getContentType(), file.getSize());
            }
        }
        return new UploadMeta(null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pathVariables(HttpServletRequest request) {
        if (request == null) {
            return Map.of();
        }
        Object value = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (value instanceof Map<?, ?> map) {
            Map<String, String> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
            return copy;
        }
        return Map.of();
    }

    private static UUID uuidFrom(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int statusOf(Throwable ex) {
        if (ex instanceof ReceiptParseException
                || ex instanceof OcrTextNotFoundException
                || ex instanceof InvalidTransactionIdException) {
            return 400;
        }
        if (ex instanceof ItemizationMismatchException) {
            return 409;
        }
        if (ex instanceof ReceiptNotFoundException || ex instanceof TransactionNotFoundException) {
            return 404;
        }
        return 500;
    }

    private static String errorCode(Throwable ex) {
        if (ex instanceof ReceiptParseException) {
            return "RECEIPT_PARSE_FAILED";
        }
        if (ex instanceof OcrTextNotFoundException) {
            return "OCR_TEXT_NOT_FOUND";
        }
        if (ex instanceof InvalidTransactionIdException) {
            return "INVALID_TRANSACTION_ID";
        }
        if (ex instanceof ItemizationMismatchException) {
            return "ITEMIZATION_MISMATCH";
        }
        if (ex instanceof ReceiptNotFoundException || ex instanceof TransactionNotFoundException) {
            return "NOT_FOUND";
        }
        return ex.getClass().getSimpleName();
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static int currentStatus(int fallback) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                && attrs.getResponse() != null) {
            int status = attrs.getResponse().getStatus();
            return status == 0 ? fallback : status;
        }
        return fallback;
    }

    private record UploadMeta(String filename, String contentType, Long size) {
    }
}
