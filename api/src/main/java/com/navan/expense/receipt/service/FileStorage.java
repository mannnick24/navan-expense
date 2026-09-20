package com.navan.expense.receipt.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorage {

    Path store(MultipartFile file);
}
