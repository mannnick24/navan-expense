package com.navan.expense.transaction.service;

import com.navan.expense.api.dto.ItemOverride;
import com.navan.expense.api.dto.PatchItemsRequest;
import com.navan.expense.api.error.InvalidTransactionIdException;
import com.navan.expense.api.error.ItemizationMismatchException;
import com.navan.expense.api.error.TransactionNotFoundException;
import com.navan.expense.parser.ParsedLineItem;
import com.navan.expense.parser.ParsedReceipt;
import com.navan.expense.parser.ParsedTax;
import com.navan.expense.parser.ReceiptParser;
import com.navan.expense.receipt.domain.Receipt;
import com.navan.expense.transaction.domain.ExpenseTransaction;
import com.navan.expense.transaction.domain.ExpenseTransactionRepository;
import com.navan.expense.transaction.domain.ItemizeStatus;
import com.navan.expense.transaction.domain.LineItem;
import com.navan.expense.transaction.domain.TaxLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private ExpenseTransactionRepository transactions;
    @Mock
    private ReceiptParser parser;
    @Mock
    private ReconciliationPolicy reconciliation;
    @InjectMocks
    private TransactionService service;

    @Test
    void getMissingIdIsNotFound() {
        UUID id = UUID.randomUUID();
        when(transactions.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id.toString())).isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void itemizeUnknownIdIsInvalid() {
        UUID id = UUID.randomUUID();
        when(transactions.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.itemize(id.toString())).isInstanceOf(InvalidTransactionIdException.class);
    }

    @Test
    void itemizeMalformedIdIsInvalid() {
        assertThatThrownBy(() -> service.itemize("not-a-uuid")).isInstanceOf(InvalidTransactionIdException.class);
    }

    @Test
    void patchUnknownIdIsInvalid() {
        assertThatThrownBy(() -> service.replaceItems("not-a-uuid", new PatchItemsRequest(List.of(
                new ItemOverride("Water", new BigDecimal("4.00"), null, null)
        )))).isInstanceOf(InvalidTransactionIdException.class);
    }

    @Test
    void patchMismatchThrows409() {
        ExpenseTransaction tx = existingTransaction();
        when(transactions.findById(tx.getId())).thenReturn(Optional.of(tx));
        when(reconciliation.reconciles(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.replaceItems(tx.getId().toString(), new PatchItemsRequest(List.of(
                new ItemOverride("Water", new BigDecimal("4.00"), null, null)
        )))).isInstanceOf(ItemizationMismatchException.class);
        assertThat(tx.getLineItems()).isEmpty();
    }

    @Test
    void createFromParsedReceiptSetsNeedsReviewWhenEmptyItems() {
        Receipt receipt = new Receipt();
        ParsedReceipt parsed = new ParsedReceipt(
                "Berlin Taxi GmbH",
                LocalDate.parse("2026-03-12"),
                "EUR",
                new BigDecimal("24.00"),
                List.of(new ParsedTax("VAT", new BigDecimal("0.19"), new BigDecimal("3.83"))),
                List.of()
        );
        when(parser.parse("ocr")).thenReturn(parsed);
        when(reconciliation.status(any(), any(), any())).thenReturn(ItemizeStatus.NEEDS_REVIEW);
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createOrUpdateFromOcr(receipt, "ocr");
        assertThat(response.itemizeStatus()).isEqualTo(ItemizeStatus.NEEDS_REVIEW);
        assertThat(response.lineItems()).isEmpty();
        assertThat(response.merchant()).isEqualTo("Berlin Taxi GmbH");
    }

    @Test
    void itemizeReplacesLineItemsOnly() {
        ExpenseTransaction tx = existingTransaction();
        tx.setMerchant("Hotel Shop");
        TaxLine tax = new TaxLine();
        tax.setName("VAT");
        tax.setAmount(new BigDecimal("1.90"));
        tx.replaceTaxes(List.of(tax));
        when(transactions.findById(tx.getId())).thenReturn(Optional.of(tx));
        when(parser.parse("ocr")).thenReturn(new ParsedReceipt(
                "Ignored Merchant",
                LocalDate.parse("2026-01-01"),
                "USD",
                new BigDecimal("99.00"),
                List.of(),
                List.of(new ParsedLineItem("Water", new BigDecimal("4.00"), null, null))
        ));
        when(reconciliation.status(any(), any(), any())).thenReturn(ItemizeStatus.NEEDS_REVIEW);

        var response = service.itemize(tx.getId().toString());
        assertThat(response.merchant()).isEqualTo("Hotel Shop");
        assertThat(response.lineItems()).extracting("description").containsExactly("Water");
        assertThat(tx.getTaxes()).hasSize(1);
    }

    private static ExpenseTransaction existingTransaction() {
        Receipt receipt = new Receipt();
        receipt.setRawOcrText("ocr");
        ExpenseTransaction tx = new ExpenseTransaction() {
            @Override
            public UUID getId() {
                return UUID.fromString("11111111-1111-1111-1111-111111111111");
            }
        };
        tx.setReceipt(receipt);
        tx.setGrandTotal(new BigDecimal("18.50"));
        return tx;
    }
}
