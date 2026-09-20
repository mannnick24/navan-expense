package com.navan.expense.receipt.api;

import com.navan.expense.api.dto.ReceiptIdResponse;
import com.navan.expense.api.dto.TransactionResponse;
import com.navan.expense.receipt.service.ReceiptService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
public class ReceiptController {

    private final ReceiptService receipts;

    public ReceiptController(ReceiptService receipts) {
        this.receipts = receipts;
    }

    @PostMapping("/receipts")
    public ReceiptIdResponse upload(@RequestParam("file") MultipartFile file) {
        return receipts.upload(file);
    }

    @PostMapping("/receipts/{id}/process")
    public TransactionResponse process(@PathVariable UUID id) {
        return receipts.process(id);
    }
}
