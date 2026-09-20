package com.navan.expense.transaction.service;

import com.navan.expense.api.dto.ItemOverride;
import com.navan.expense.api.dto.LineItemResponse;
import com.navan.expense.api.dto.PatchItemsRequest;
import com.navan.expense.api.dto.TaxResponse;
import com.navan.expense.api.dto.TransactionResponse;
import com.navan.expense.api.error.InvalidTransactionIdException;
import com.navan.expense.api.error.ItemizationMismatchException;
import com.navan.expense.api.error.OcrTextNotFoundException;
import com.navan.expense.api.error.TransactionNotFoundException;
import com.navan.expense.parser.ParsedLineItem;
import com.navan.expense.parser.ParsedReceipt;
import com.navan.expense.parser.ParsedTax;
import com.navan.expense.parser.ReceiptParser;
import com.navan.expense.receipt.domain.Receipt;
import com.navan.expense.transaction.domain.ExpenseTransaction;
import com.navan.expense.transaction.domain.ExpenseTransactionRepository;
import com.navan.expense.transaction.domain.LineItem;
import com.navan.expense.transaction.domain.TaxLine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private final ExpenseTransactionRepository transactions;
    private final ReceiptParser parser;
    private final ReconciliationPolicy reconciliation;

    public TransactionService(
            ExpenseTransactionRepository transactions,
            ReceiptParser parser,
            ReconciliationPolicy reconciliation
    ) {
        this.transactions = transactions;
        this.parser = parser;
        this.reconciliation = reconciliation;
    }

    @Transactional
    public TransactionResponse createOrUpdateFromOcr(Receipt receipt, String rawOcr) {
        ParsedReceipt parsed = parser.parse(rawOcr);
        ExpenseTransaction tx = receipt.getTransaction();
        if (tx == null) {
            tx = new ExpenseTransaction();
            tx.setReceipt(receipt);
        }
        applyParsed(tx, parsed, true);
        return toResponse(transactions.save(tx));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(String id) {
        return toResponse(loadForLookup(id));
    }

    @Transactional
    public TransactionResponse itemize(String id) {
        ExpenseTransaction tx = loadForCommand(id);
        String rawOcr = tx.getReceipt().getRawOcrText();
        if (rawOcr == null || rawOcr.isBlank()) {
            throw new OcrTextNotFoundException();
        }
        ParsedReceipt parsed = parser.parse(rawOcr);
        applyParsed(tx, parsed, false);
        return toResponse(tx);
    }

    @Transactional
    public TransactionResponse replaceItems(String id, PatchItemsRequest request) {
        ExpenseTransaction tx = loadForCommand(id);
        List<LineItem> next = request.items().stream().map(this::toLineItem).toList();
        List<BigDecimal> itemAmounts = next.stream().map(LineItem::getAmount).toList();
        List<BigDecimal> taxAmounts = tx.getTaxes().stream().map(TaxLine::getAmount).toList();
        if (!reconciliation.reconciles(tx.getGrandTotal(), itemAmounts, taxAmounts)) {
            throw new ItemizationMismatchException(tx.getGrandTotal(), sum(itemAmounts), sum(taxAmounts));
        }
        tx.replaceLineItems(next);
        tx.setItemizeStatus(reconciliation.status(tx.getGrandTotal(), itemAmounts, taxAmounts));
        return toResponse(tx);
    }

    private void applyParsed(ExpenseTransaction tx, ParsedReceipt parsed, boolean replaceHeaderAndTaxes) {
        if (replaceHeaderAndTaxes) {
            tx.setMerchant(parsed.merchant());
            tx.setDate(parsed.date());
            tx.setCurrency(parsed.currency());
            tx.setGrandTotal(parsed.grandTotal());
            tx.replaceTaxes(parsed.taxes().stream().map(this::toTaxLine).toList());
        }
        tx.replaceLineItems(parsed.lineItems().stream().map(this::toLineItem).toList());
        List<BigDecimal> itemAmounts = tx.getLineItems().stream().map(LineItem::getAmount).toList();
        List<BigDecimal> taxAmounts = tx.getTaxes().stream().map(TaxLine::getAmount).toList();
        tx.setItemizeStatus(reconciliation.status(tx.getGrandTotal(), itemAmounts, taxAmounts));
    }

    private ExpenseTransaction loadForLookup(String id) {
        UUID uuid = parseUuid(id, false);
        return transactions.findById(uuid).orElseThrow(() -> new TransactionNotFoundException(id));
    }

    private ExpenseTransaction loadForCommand(String id) {
        UUID uuid = parseUuid(id, true);
        return transactions.findById(uuid).orElseThrow(() -> new InvalidTransactionIdException(id));
    }

    private static UUID parseUuid(String id, boolean command) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            if (command) {
                throw new InvalidTransactionIdException(id);
            }
            throw new TransactionNotFoundException(id);
        }
    }

    private TaxLine toTaxLine(ParsedTax parsed) {
        TaxLine tax = new TaxLine();
        tax.setName(parsed.name());
        tax.setRate(parsed.rate());
        tax.setAmount(parsed.amount());
        return tax;
    }

    private LineItem toLineItem(ParsedLineItem parsed) {
        LineItem item = new LineItem();
        item.setDescription(parsed.description());
        item.setAmount(parsed.amount());
        item.setTaxAmount(parsed.taxAmount());
        item.setQuantity(parsed.quantity());
        return item;
    }

    private LineItem toLineItem(ItemOverride override) {
        LineItem item = new LineItem();
        item.setDescription(override.description());
        item.setAmount(override.amount());
        item.setTaxAmount(override.taxAmount());
        item.setQuantity(override.quantity());
        return item;
    }

    private TransactionResponse toResponse(ExpenseTransaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getReceipt().getId(),
                tx.getMerchant(),
                tx.getDate(),
                tx.getCurrency(),
                tx.getGrandTotal(),
                tx.getTaxes().stream().map(tax -> new TaxResponse(tax.getName(), tax.getRate(), tax.getAmount())).toList(),
                tx.getLineItems().stream()
                        .map(item -> new LineItemResponse(
                                item.getId(),
                                item.getDescription(),
                                item.getAmount(),
                                item.getTaxAmount(),
                                item.getQuantity()
                        ))
                        .toList(),
                tx.getItemizeStatus(),
                tx.getReceipt().getRawOcrText()
        );
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().map(value -> value == null ? BigDecimal.ZERO : value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
