package com.navan.expense.config;

import com.navan.expense.parser.ReceiptParser;
import com.navan.expense.parser.ReceiptParserFactory;
import com.navan.expense.parser.ReceiptParserKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
public class ParserConfiguration {

    @Bean
    ReceiptParserFactory receiptParserFactory() {
        return new ReceiptParserFactory();
    }

    @Bean
    ReceiptParser receiptParser(
            ReceiptParserFactory factory,
            @Value("${app.parser.kind:regex}") String kind
    ) {
        return factory.create(parseKind(kind));
    }

    private static ReceiptParserKind parseKind(String kind) {
        try {
            return ReceiptParserKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown app.parser.kind '" + kind + "'; use regex", ex);
        }
    }
}
