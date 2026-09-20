package com.navan.expense.receipt.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

public class ClasspathReceiptTextResolver implements ReceiptTextResolver {

    @Override
    public Optional<String> resolve(String originalFilename, Path storedPath) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return Optional.empty();
        }
        String resource = stem(originalFilename) + ".txt";
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Upload names may include a client path ({@code C:\tmp\a.png} or {@code /tmp/a.png}).
     * {@link Path} uses this host's separators, so backslashes are normalized first.
     */
    private static String stem(String originalFilename) {
        String filename = Path.of(originalFilename.replace('\\', '/')).getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
