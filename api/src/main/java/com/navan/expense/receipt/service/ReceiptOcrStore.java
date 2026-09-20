package com.navan.expense.receipt.service;

import com.navan.expense.api.error.ReceiptNotFoundException;
import com.navan.expense.receipt.domain.Receipt;
import com.navan.expense.receipt.domain.ReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReceiptOcrStore {

    private final ReceiptRepository receipts;

    public ReceiptOcrStore(ReceiptRepository receipts) {
        this.receipts = receipts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(UUID receiptId, String text) {
        Receipt receipt = receipts.findById(receiptId).orElseThrow(() -> new ReceiptNotFoundException(receiptId));
        receipt.setRawOcrText(text);
        receipts.saveAndFlush(receipt);
    }
}
