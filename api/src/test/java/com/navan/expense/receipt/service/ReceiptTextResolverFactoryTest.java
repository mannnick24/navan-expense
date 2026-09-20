package com.navan.expense.receipt.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptTextResolverFactoryTest {

    private final ReceiptTextResolverFactory factory = new ReceiptTextResolverFactory();

    @Test
    void createDefaultsToClasspathFixtureResolver() {
        assertThat(factory.create()).isInstanceOf(ClasspathReceiptTextResolver.class);
    }

    @Test
    void createFixtureKindReturnsClasspathResolver() {
        assertThat(factory.create(ReceiptTextResolverKind.FIXTURE)).isInstanceOf(ClasspathReceiptTextResolver.class);
    }

    @Test
    void createOcrKindReturnsVendorStub() {
        ReceiptTextResolver resolver = factory.create(ReceiptTextResolverKind.OCR);
        assertThat(resolver).isInstanceOf(StubOcrReceiptTextResolver.class);
        assertThatThrownBy(() -> resolver.resolve("receipt-clean.png", Path.of("unused.png")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Vendor OCR is not wired");
    }
}
