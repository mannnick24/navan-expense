package com.navan.expense.config;

import com.navan.expense.parser.ReceiptParser;
import com.navan.expense.parser.ReceiptParserFactory;
import com.navan.expense.parser.ReceiptParserKind;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ParserConfiguration {

    @Bean
    ReceiptParserFactory receiptParserFactory() {
        return new ReceiptParserFactory();
    }

    @Bean
    ReceiptParser receiptParser(ReceiptParserFactory factory) {
        return factory.create(ReceiptParserKind.REGEX);
    }
}
