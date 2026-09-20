package com.navan.expense.transaction.api;

import com.navan.expense.api.dto.PatchItemsRequest;
import com.navan.expense.api.dto.TransactionResponse;
import com.navan.expense.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionController {

    private final TransactionService transactions;

    public TransactionController(TransactionService transactions) {
        this.transactions = transactions;
    }

    @GetMapping("/transactions/{id}")
    public TransactionResponse get(@PathVariable String id) {
        return transactions.get(id);
    }

    @PostMapping("/transactions/{id}/itemize")
    public TransactionResponse itemize(@PathVariable String id) {
        return transactions.itemize(id);
    }

    @PatchMapping("/transactions/{id}/items")
    public TransactionResponse replaceItems(@PathVariable String id, @Valid @RequestBody PatchItemsRequest request) {
        return transactions.replaceItems(id, request);
    }
}
