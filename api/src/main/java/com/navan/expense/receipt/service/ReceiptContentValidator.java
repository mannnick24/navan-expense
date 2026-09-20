package com.navan.expense.receipt.service;

import com.navan.expense.api.error.InvalidReceiptFileException;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class ReceiptContentValidator {

    private static final int SNIFF_LENGTH = 16;
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF87 = {'G', 'I', 'F', '8', '7', 'a'};
    private static final byte[] GIF89 = {'G', 'I', 'F', '8', '9', 'a'};
    private static final byte[] BMP = {'B', 'M'};
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    private static final Map<String, Kind> MIME_TO_KIND = Map.of(
            "image/png", Kind.PNG,
            "image/jpeg", Kind.JPEG,
            "image/jpg", Kind.JPEG,
            "image/gif", Kind.GIF,
            "image/webp", Kind.WEBP,
            "image/bmp", Kind.BMP,
            "application/pdf", Kind.PDF
    );

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidReceiptFileException("Receipt file is required");
        }
        Kind declared = declaredKind(file.getContentType());
        Kind sniffed = sniff(readPrefix(file)).orElseThrow(() -> new InvalidReceiptFileException(
                "File signature is not a supported image or PDF"
        ));
        if (declared != sniffed) {
            throw new InvalidReceiptFileException(
                    "Declared content type " + declared.mime + " does not match file signature (" + sniffed.mime + ")"
            );
        }
    }

    private static Kind declaredKind(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new InvalidReceiptFileException("Content type is required; allow image/* (png, jpeg, gif, webp, bmp) or application/pdf");
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(rawType);
            String key = mediaType.getType().toLowerCase(Locale.ROOT) + "/" + mediaType.getSubtype().toLowerCase(Locale.ROOT);
            Kind kind = MIME_TO_KIND.get(key);
            if (kind == null) {
                throw new InvalidReceiptFileException("Unsupported content type " + key + "; allow image or PDF");
            }
            return kind;
        } catch (InvalidMediaTypeException ex) {
            throw new InvalidReceiptFileException("Invalid content type '" + rawType + "'");
        }
    }

    private static Optional<Kind> sniff(byte[] prefix) {
        if (startsWith(prefix, PNG)) {
            return Optional.of(Kind.PNG);
        }
        if (startsWith(prefix, JPEG)) {
            return Optional.of(Kind.JPEG);
        }
        if (startsWith(prefix, GIF87) || startsWith(prefix, GIF89)) {
            return Optional.of(Kind.GIF);
        }
        if (startsWith(prefix, BMP)) {
            return Optional.of(Kind.BMP);
        }
        if (startsWith(prefix, PDF)) {
            return Optional.of(Kind.PDF);
        }
        if (startsWith(prefix, RIFF) && prefix.length >= 12 && Arrays.equals(Arrays.copyOfRange(prefix, 8, 12), WEBP)) {
            return Optional.of(Kind.WEBP);
        }
        return Optional.empty();
    }

    private static byte[] readPrefix(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SNIFF_LENGTH);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read receipt file", ex);
        }
    }

    private static boolean startsWith(byte[] haystack, byte[] needle) {
        if (haystack.length < needle.length) {
            return false;
        }
        for (int i = 0; i < needle.length; i++) {
            if (haystack[i] != needle[i]) {
                return false;
            }
        }
        return true;
    }

    private enum Kind {
        PNG("image/png"),
        JPEG("image/jpeg"),
        GIF("image/gif"),
        WEBP("image/webp"),
        BMP("image/bmp"),
        PDF("application/pdf");

        private final String mime;

        Kind(String mime) {
            this.mime = mime;
        }
    }
}
