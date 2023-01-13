package com.uid2.admin.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class TmpFileStorage implements FileStorage {
    @Override
    public String create(FileName fileName, String content) throws IOException {
        Path newFile = Files.createTempFile(fileName.getPrefix(), fileName.getSuffix());
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(newFile, contentBytes, StandardOpenOption.CREATE);
        return newFile.toString();
    }

    @Override
    public String create(FileName fileName, InputStream content) throws IOException {
        Path newFile = Files.createTempFile(fileName.getPrefix(), fileName.getSuffix());
        Files.copy(content, newFile, StandardCopyOption.REPLACE_EXISTING);
        return newFile.toString();
    }
}
