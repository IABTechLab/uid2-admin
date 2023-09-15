package com.uid2.admin;

import io.vertx.core.json.JsonArray;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TestUtilites {
    public static ByteArrayInputStream toInputStream(String data) {
        return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
    }

    public static InputStream makeInputStream(JsonArray content) {
        return toInputStream(content.toString());
    }
}
