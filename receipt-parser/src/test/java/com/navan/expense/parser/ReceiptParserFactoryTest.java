package com.navan.expense.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptParserFactoryTest {

    private final ReceiptParserFactory factory = new ReceiptParserFactory();

    @Test
    void createDefaultsToRegexParser() {
        assertThat(factory.create()).isInstanceOf(RegexReceiptParser.class);
    }

    @Test
    void createRegexKindReturnsRegexParser() {
        assertThat(factory.create(ReceiptParserKind.REGEX)).isInstanceOf(RegexReceiptParser.class);
    }
}
