package com.navan.expense.receipt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${app.storage.dir}") String dir) {
        this.root = Path.of(dir);
    }

    @Override
    public Path store(MultipartFile file) {
        try {
            Files.createDirectories(root);
            String original = file.getOriginalFilename() == null ? "receipt.bin" : file.getOriginalFilename();
            Path target = root.resolve(UUID.randomUUID() + "-" + Path.of(original).getFileName());
            file.transferTo(target);
            return target;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store receipt file", ex);
        }
    }
}
