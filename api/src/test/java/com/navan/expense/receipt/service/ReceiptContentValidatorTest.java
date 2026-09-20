package com.navan.expense.receipt.service;

import com.navan.expense.api.error.InvalidReceiptFileException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptContentValidatorTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a'};

    private final ReceiptContentValidator validator = new ReceiptContentValidator();

    @Test
    void acceptsPngJpegGifAndPdfWhenHeaderMatchesBytes() {
        assertThatCode(() -> validator.validate(file("a.png", "image/png", PNG))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("a.jpg", "image/jpeg", JPEG))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("a.jpg", "image/jpg", JPEG))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("a.gif", "image/gif", GIF))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("a.pdf", "application/pdf", PDF))).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingEmptyOrUnsupportedDeclaredType() {
        assertThatThrownBy(() -> validator.validate(file("a.png", null, PNG)))
                .isInstanceOf(InvalidReceiptFileException.class)
                .hasMessageContaining("Content type is required");
        assertThatThrownBy(() -> validator.validate(file("a.txt", "text/plain", PNG)))
                .isInstanceOf(InvalidReceiptFileException.class)
                .hasMessageContaining("Unsupported content type");
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("file", "a.png", "image/png", new byte[0])))
                .isInstanceOf(InvalidReceiptFileException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsWhenMagicBytesDoNotMatchDeclaredType() {
        assertThatThrownBy(() -> validator.validate(file("a.png", "image/png", PDF)))
                .isInstanceOf(InvalidReceiptFileException.class)
                .hasMessageContaining("does not match file signature");
        assertThatThrownBy(() -> validator.validate(file("a.png", "image/png", "not-an-image".getBytes())))
                .isInstanceOf(InvalidReceiptFileException.class)
                .hasMessageContaining("File signature is not a supported image or PDF");
    }

    private static MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }
}
