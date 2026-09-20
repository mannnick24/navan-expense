package com.navan.expense.config;

import com.navan.expense.receipt.service.ReceiptTextResolver;
import com.navan.expense.receipt.service.ReceiptTextResolverFactory;
import com.navan.expense.receipt.service.ReceiptTextResolverKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
public class OcrConfiguration {

    @Bean
    ReceiptTextResolverFactory receiptTextResolverFactory() {
        return new ReceiptTextResolverFactory();
    }

    @Bean
    ReceiptTextResolver receiptTextResolver(
            ReceiptTextResolverFactory factory,
            @Value("${app.ocr.kind:fixture}") String kind
    ) {
        return factory.create(parseKind(kind));
    }

    private static ReceiptTextResolverKind parseKind(String kind) {
        try {
            return ReceiptTextResolverKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown app.ocr.kind '" + kind + "'; use fixture or ocr", ex);
        }
    }
}
