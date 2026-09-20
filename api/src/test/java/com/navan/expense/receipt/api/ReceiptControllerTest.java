package com.navan.expense.receipt.api;

import com.navan.expense.api.ApiExceptionHandler;
import com.navan.expense.api.dto.ReceiptIdResponse;
import com.navan.expense.api.dto.TransactionResponse;
import com.navan.expense.api.error.OcrTextNotFoundException;
import com.navan.expense.parser.ReceiptParseException;
import com.navan.expense.receipt.service.ReceiptService;
import com.navan.expense.transaction.domain.ItemizeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReceiptController.class)
@Import(ApiExceptionHandler.class)
class ReceiptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptService receipts;

    @Test
    void uploadReturnsReceiptId() throws Exception {
        UUID id = UUID.randomUUID();
        when(receipts.upload(any())).thenReturn(new ReceiptIdResponse(id));
        MockMultipartFile file = new MockMultipartFile("file", "receipt-clean.png", "image/png", "x".getBytes());
        mockMvc.perform(multipart("/receipts").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receipt_id").value(id.toString()));
    }

    @Test
    void processReturnsTransaction() throws Exception {
        UUID receiptId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        when(receipts.process(receiptId)).thenReturn(new TransactionResponse(
                txId, receiptId, "Cafe Mitte", LocalDate.parse("2026-03-12"), "EUR",
                new BigDecimal("17.85"), List.of(), List.of(), ItemizeStatus.COMPLETE, "ocr"
        ));
        mockMvc.perform(post("/receipts/{id}/process", receiptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction_id").value(txId.toString()))
                .andExpect(jsonPath("$.merchant").value("Cafe Mitte"));
    }

    @Test
    void processParseFailureIs400() throws Exception {
        UUID receiptId = UUID.randomUUID();
        when(receipts.process(receiptId)).thenThrow(new ReceiptParseException(List.of(
                new ReceiptParseException.FieldError("merchant", "MISSING")
        )));
        mockMvc.perform(post("/receipts/{id}/process", receiptId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("RECEIPT_PARSE_FAILED"))
                .andExpect(jsonPath("$.fields[0].name").value("merchant"));
    }

    @Test
    void unknownFilenameIs400() throws Exception {
        UUID receiptId = UUID.randomUUID();
        when(receipts.process(receiptId)).thenThrow(new OcrTextNotFoundException());
        mockMvc.perform(post("/receipts/{id}/process", receiptId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("OCR_TEXT_NOT_FOUND"));
    }
}
