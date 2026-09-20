package com.navan.expense.receipt.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathReceiptTextResolverTest {

    private final ReceiptTextResolver resolver = new ClasspathReceiptTextResolver();

    @Test
    void mapsUploadFilenameToFixtureText() {
        assertThat(resolver.resolve("receipt-clean.png")).get().asString().contains("Cafe Mitte");
        assertThat(resolver.resolve("receipt-tax-only.jpg")).get().asString().contains("Berlin Taxi GmbH");
        assertThat(resolver.resolve("unknown.bin")).isEmpty();
    }
}
