package com.uid2.admin.store.writer.mocks;

import com.uid2.admin.store.FileStorage;
import com.uid2.admin.store.FileName;
import com.uid2.shared.cloud.InMemoryStorageMock;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

// For InMemoryStorageMock from Shared to work, the code needs to write to its own
// "filesystem". This test FileStorage implementation delegates to it so that
// we can use InMemoryStorageMock in tests
// If we stop writing to disk every time we upload/backup file we can get rid of this
public class FileStorageMock implements FileStorage {
    private final InMemoryStorageMock localStackStorageMock;

    public FileStorageMock(InMemoryStorageMock localStackStorageMock) {
        this.localStackStorageMock = localStackStorageMock;
    }

    @Override
    public String create(FileName fileName, String content) {
        String path = "/tmp/" + fileName.toString();
        localStackStorageMock.save(content.getBytes(), path);
        return path;
    }

    @Override
    public String create(FileName fileName, InputStream content) {
        String bufferedContent = new BufferedReader(new InputStreamReader(content))
                .lines().collect(Collectors.joining("\n"));
        return create(fileName, bufferedContent);
    }
}
