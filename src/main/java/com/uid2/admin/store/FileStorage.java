package com.uid2.admin.store;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorage {
    String create(FileName fileName, String content) throws IOException;

    String create(FileName fileName, InputStream content) throws IOException;
}
