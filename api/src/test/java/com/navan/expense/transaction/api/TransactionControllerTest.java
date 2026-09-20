package com.navan.expense.transaction.api;

import com.navan.expense.api.ApiExceptionHandler;
import com.navan.expense.api.dto.TransactionResponse;
import com.navan.expense.api.error.InvalidTransactionIdException;
import com.navan.expense.api.error.ItemizationMismatchException;
import com.navan.expense.api.error.TransactionNotFoundException;
import com.navan.expense.transaction.domain.ItemizeStatus;
import com.navan.expense.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransactionController.class)
@Import(ApiExceptionHandler.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactions;

    @Test
    void getMissingIs404() throws Exception {
        when(transactions.get("missing")).thenThrow(new TransactionNotFoundException("missing"));
        mockMvc.perform(get("/transactions/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void itemizeInvalidIdIs400() throws Exception {
        when(transactions.itemize("not-a-uuid")).thenThrow(new InvalidTransactionIdException("not-a-uuid"));
        mockMvc.perform(post("/transactions/not-a-uuid/itemize"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TRANSACTION_ID"));
    }

    @Test
    void itemsInvalidIdIs400() throws Exception {
        when(transactions.replaceItems(eq("not-a-uuid"), any())).thenThrow(new InvalidTransactionIdException("not-a-uuid"));
        mockMvc.perform(patch("/transactions/not-a-uuid/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"description":"Water","amount":4.00}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TRANSACTION_ID"));
    }

    @Test
    void itemsMismatchIs409() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactions.replaceItems(eq(id.toString()), any()))
                .thenThrow(new ItemizationMismatchException(new BigDecimal("18.50"), new BigDecimal("10.00"), new BigDecimal("1.90")));
        mockMvc.perform(patch("/transactions/{id}/items", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"description":"Water","amount":4.00},{"description":"Snacks","amount":6.00}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ITEMIZATION_MISMATCH"))
                .andExpect(jsonPath("$.grand_total").value(18.50));
    }

    @Test
    void getReturnsTransaction() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactions.get(id.toString())).thenReturn(new TransactionResponse(
                id, UUID.randomUUID(), "Cafe Mitte", LocalDate.parse("2026-03-12"), "EUR",
                new BigDecimal("17.85"), List.of(), List.of(), ItemizeStatus.COMPLETE, "ocr"
        ));
        mockMvc.perform(get("/transactions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("Cafe Mitte"))
                .andExpect(jsonPath("$.itemize_status").value("COMPLETE"));
    }
}
