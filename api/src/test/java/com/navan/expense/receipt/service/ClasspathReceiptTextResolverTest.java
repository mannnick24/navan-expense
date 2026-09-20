package com.navan.expense.receipt.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathReceiptTextResolverTest {

    private final ReceiptTextResolver resolver = new ClasspathReceiptTextResolver();

    @Test
    void mapsUploadFilenameToFixtureText() {
        assertThat(resolver.resolve("receipt-clean.png", null)).get().asString().contains("Cafe Mitte");
        assertThat(resolver.resolve("receipt-tax-only.jpg", null)).get().asString().contains("Berlin Taxi GmbH");
        assertThat(resolver.resolve("unknown.bin", null)).isEmpty();
    }

    @Test
    void mapsUploadPathToFixtureText() {
        assertThat(resolver.resolve("/tmp/uploads/receipt-clean.png", null)).get().asString().contains("Cafe Mitte");
        assertThat(resolver.resolve("C:\\Users\\me\\receipt-clean.png", null)).get().asString().contains("Cafe Mitte");
    }
}
