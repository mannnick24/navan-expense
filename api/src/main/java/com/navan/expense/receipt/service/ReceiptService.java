package com.navan.expense.receipt.service;

import com.navan.expense.api.dto.ReceiptIdResponse;
import com.navan.expense.api.dto.TransactionResponse;
import com.navan.expense.api.error.OcrTextNotFoundException;
import com.navan.expense.api.error.ReceiptNotFoundException;
import com.navan.expense.receipt.domain.Receipt;
import com.navan.expense.receipt.domain.ReceiptRepository;
import com.navan.expense.transaction.service.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Service
public class ReceiptService {

    private final ReceiptRepository receipts;
    private final FileStorage fileStorage;
    private final ReceiptContentValidator contentValidator;
    private final ReceiptTextResolver textResolver;
    private final ReceiptOcrStore ocrStore;
    private final TransactionService transactions;

    public ReceiptService(
            ReceiptRepository receipts,
            FileStorage fileStorage,
            ReceiptContentValidator contentValidator,
            ReceiptTextResolver textResolver,
            ReceiptOcrStore ocrStore,
            TransactionService transactions
    ) {
        this.receipts = receipts;
        this.fileStorage = fileStorage;
        this.contentValidator = contentValidator;
        this.textResolver = textResolver;
        this.ocrStore = ocrStore;
        this.transactions = transactions;
    }

    @Transactional
    public ReceiptIdResponse upload(MultipartFile file) {
        contentValidator.validate(file);
        Receipt receipt = new Receipt();
        receipt.setOriginalFilename(file.getOriginalFilename());
        receipt.setStoredPath(fileStorage.store(file).toString());
        receipt.setContentType(file.getContentType());
        receipt.setCreatedAt(Instant.now());
        return new ReceiptIdResponse(receipts.save(receipt).getId());
    }

    public TransactionResponse process(UUID receiptId) {
        Receipt receipt = receipts.findById(receiptId).orElseThrow(() -> new ReceiptNotFoundException(receiptId));
        Path stored = receipt.getStoredPath() == null ? null : Path.of(receipt.getStoredPath());
        String text = textResolver.resolve(receipt.getOriginalFilename(), stored).orElseThrow(OcrTextNotFoundException::new);
        ocrStore.store(receiptId, text);
        receipt.setRawOcrText(text);
        return transactions.createOrUpdateFromOcr(receipt, text);
    }
}
