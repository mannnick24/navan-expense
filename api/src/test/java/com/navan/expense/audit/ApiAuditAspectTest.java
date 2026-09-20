package com.navan.expense.audit;

import com.navan.expense.api.error.InvalidTransactionIdException;
import com.navan.expense.api.error.ItemizationMismatchException;
import com.navan.expense.api.error.OcrTextNotFoundException;
import com.navan.expense.api.error.TransactionNotFoundException;
import com.navan.expense.parser.ReceiptParseException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiAuditAspectTest {

    @Mock
    private AuditPublisher publisher;
    @Mock
    private ProceedingJoinPoint joinPoint;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void publishesSuccessWithoutRequestBody() throws Throwable {
        ApiAuditAspect aspect = new ApiAuditAspect(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/transactions/11111111-1111-1111-1111-111111111111");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "11111111-1111-1111-1111-111111111111"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        try {
            assertThat(aspect.audit(joinPoint)).isEqualTo("ok");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.outcome()).isEqualTo("SUCCESS");
        assertThat(event.httpMethod()).isEqualTo("GET");
        assertThat(event.transactionId()).isNotNull();
        assertThat(event.receiptId()).isNull();
    }

    @Test
    void logsMultipartFilenameOnly() throws Throwable {
        ApiAuditAspect aspect = new ApiAuditAspect(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/receipts");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MockMultipartFile file = new MockMultipartFile("file", "receipt-clean.png", "image/png", "secret-bytes".getBytes());
        when(joinPoint.getArgs()).thenReturn(new Object[]{file});
        when(joinPoint.proceed()).thenReturn("ok");

        try {
            aspect.audit(joinPoint);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.uploadFilename()).isEqualTo("receipt-clean.png");
        assertThat(event.uploadContentType()).isEqualTo("image/png");
        assertThat(event.uploadSize()).isEqualTo(12L);
        assertThat(event.toString()).doesNotContain("secret-bytes");
    }

    @Test
    void recordsParseFailureAndRethrows() throws Throwable {
        ApiAuditAspect aspect = new ApiAuditAspect(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/receipts/11111111-1111-1111-1111-111111111111/process");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "11111111-1111-1111-1111-111111111111"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ReceiptParseException ex = new ReceiptParseException(List.of(new ReceiptParseException.FieldError("merchant", "MISSING")));
        when(joinPoint.proceed()).thenThrow(ex);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        try {
            assertThatThrownBy(() -> aspect.audit(joinPoint)).isSameAs(ex);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(400);
        assertThat(captor.getValue().error()).isEqualTo("RECEIPT_PARSE_FAILED");
        assertThat(captor.getValue().receiptId()).isNotNull();
    }

    @Test
    void recordsInvalidId() throws Throwable {
        ApiAuditAspect aspect = new ApiAuditAspect(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/transactions/not-a-uuid/itemize");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(joinPoint.proceed()).thenThrow(new InvalidTransactionIdException("not-a-uuid"));
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        try {
            assertThatThrownBy(() -> aspect.audit(joinPoint)).isInstanceOf(InvalidTransactionIdException.class);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(400);
        assertThat(captor.getValue().error()).isEqualTo("INVALID_TRANSACTION_ID");
    }

    @Test
    void recordsOcrMissingAs400() throws Throwable {
        bind(request("POST", "/receipts/11111111-1111-1111-1111-111111111111/process"));
        when(joinPoint.proceed()).thenThrow(new OcrTextNotFoundException());
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        ApiAuditAspect aspect = new ApiAuditAspect(publisher);
        assertThatThrownBy(() -> aspect.audit(joinPoint)).isInstanceOf(OcrTextNotFoundException.class);

        AuditEvent event = published();
        assertThat(event.status()).isEqualTo(400);
        assertThat(event.error()).isEqualTo("OCR_TEXT_NOT_FOUND");
        assertThat(event.outcome()).isEqualTo("ERROR");
    }

    @Test
    void recordsMismatchAs409AndRethrows() throws Throwable {
        bind(request("PATCH", "/transactions/11111111-1111-1111-1111-111111111111/items"));
        ItemizationMismatchException ex = new ItemizationMismatchException(
                new BigDecimal("17.85"), new BigDecimal("10.00"), new BigDecimal("2.85"));
        when(joinPoint.proceed()).thenThrow(ex);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        ApiAuditAspect aspect = new ApiAuditAspect(publisher);
        assertThatThrownBy(() -> aspect.audit(joinPoint)).isSameAs(ex);

        AuditEvent event = published();
        assertThat(event.status()).isEqualTo(409);
        assertThat(event.error()).isEqualTo("ITEMIZATION_MISMATCH");
        assertThat(event.transactionId()).isNotNull();
        assertThat(event.receiptId()).isNull();
    }

    @Test
    void recordsMissingTransactionAs404() throws Throwable {
        bind(request("GET", "/transactions/11111111-1111-1111-1111-111111111111"));
        when(joinPoint.proceed()).thenThrow(new TransactionNotFoundException("11111111-1111-1111-1111-111111111111"));
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        ApiAuditAspect aspect = new ApiAuditAspect(publisher);
        assertThatThrownBy(() -> aspect.audit(joinPoint)).isInstanceOf(TransactionNotFoundException.class);

        AuditEvent event = published();
        assertThat(event.status()).isEqualTo(404);
        assertThat(event.error()).isEqualTo("NOT_FOUND");
    }

    @Test
    void unexpectedFailureIs500AndRethrown() throws Throwable {
        bind(request("GET", "/transactions/11111111-1111-1111-1111-111111111111"));
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        ApiAuditAspect aspect = new ApiAuditAspect(publisher);
        assertThatThrownBy(() -> aspect.audit(joinPoint)).isInstanceOf(IllegalStateException.class);

        AuditEvent event = published();
        assertThat(event.status()).isEqualTo(500);
        assertThat(event.error()).isEqualTo("IllegalStateException");
        assertThat(event.outcome()).isEqualTo("ERROR");
    }

    @Test
    void capturesQueryAndDurationOnSuccess() throws Throwable {
        MockHttpServletRequest request = request("GET", "/transactions/11111111-1111-1111-1111-111111111111");
        request.setQueryString("include=raw");
        bind(request);
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        ApiAuditAspect aspect = new ApiAuditAspect(publisher);
        aspect.audit(joinPoint);

        AuditEvent event = published();
        assertThat(event.query()).isEqualTo("include=raw");
        assertThat(event.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(event.error()).isNull();
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (path.contains("11111111-1111-1111-1111-111111111111")) {
            request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "11111111-1111-1111-1111-111111111111"));
        }
        return request;
    }

    private static void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private AuditEvent published() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }
}
