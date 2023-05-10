package com.uid2.admin.store;

import com.uid2.shared.cloud.CloudStorageException;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.util.List;

public class FileManager {
    private final ICloudStorage cloudStorage;
    private final FileStorage fileStorage;

    public FileManager(ICloudStorage cloudStorage, FileStorage fileStorage) {
        this.cloudStorage = cloudStorage;
        this.fileStorage = fileStorage;
    }

    public void uploadFile(CloudPath location, FileName fileName, String content) throws IOException, CloudStorageException {
        String newFile = fileStorage.create(fileName, content);
        cloudStorage.upload(newFile, location.toString());
    }

    public void uploadMetadata(JsonObject metadata, String name, CloudPath location) throws Exception {
        FileName fileName = new FileName(name + "-metadata", ".json");
        String content = Json.encodePrettily(metadata);
        uploadFile(location, fileName, content);
    }

    public boolean isPresent(CloudPath path) throws CloudStorageException {
        List<String> files = cloudStorage.list(path.toString());
        return !files.isEmpty();
    }
}
