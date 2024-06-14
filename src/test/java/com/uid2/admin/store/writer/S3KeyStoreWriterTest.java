package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class S3KeyStoreWriterTest {

    private StoreReader<Map<Integer, S3Key>> provider;
    private FileManager fileManager;
    private ObjectWriter jsonWriter;
    private VersionGenerator versionGenerator;
    private Clock clock;
    private StoreScope scope;
    private ScopedStoreWriter scopedStoreWriter;
    private S3KeyStoreWriter s3KeyStoreWriter;

    @BeforeEach
    void setUp() {
        provider = mock(StoreReader.class);
        fileManager = mock(FileManager.class);
        jsonWriter = mock(ObjectWriter.class);
        versionGenerator = mock(VersionGenerator.class);
        clock = mock(Clock.class);
        scope = mock(StoreScope.class);
        scopedStoreWriter = mock(ScopedStoreWriter.class);

        s3KeyStoreWriter = new S3KeyStoreWriter(provider, fileManager, jsonWriter, versionGenerator, clock, scope);
    }

    @Test
    void testUpload() throws Exception {
        Map<Integer, S3Key> data = new HashMap<>();
        data.put(1, new S3Key(1, 123, 1687635529, 1687808329, "S3keySecretByteHere1"));
        data.put(2, new S3Key(2, 123, 1687808429, 1687808329, "S3keySecretByteHere2"));
        data.put(3, new S3Key(3, 456, 1687635529, 1687808329, "S3keySecretByteHere3"));

        JsonObject extraMeta = new JsonObject();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonObject> metaCaptor = ArgumentCaptor.forClass(JsonObject.class);

        s3KeyStoreWriter.upload(data, extraMeta);

        verify(scopedStoreWriter).upload(jsonCaptor.capture(), metaCaptor.capture());

        JsonArray jsonArray = new JsonArray(jsonCaptor.getValue());
        assertEquals(3, jsonArray.size());

        JsonObject jsonKey1 = jsonArray.getJsonObject(0);
        assertEquals(1, jsonKey1.getInteger("id"));
        assertEquals(123, jsonKey1.getInteger("site_id"));
        assertEquals(1687635529L, jsonKey1.getLong("activates"));
        assertEquals(1687808329L, jsonKey1.getLong("created"));
        assertEquals("S3keySecretByteHere1", jsonKey1.getString("secret"));

        JsonObject jsonKey2 = jsonArray.getJsonObject(1);
        assertEquals(2, jsonKey2.getInteger("id"));
        assertEquals(123, jsonKey2.getInteger("site_id"));
        assertEquals(1687808429L, jsonKey2.getLong("activates"));
        assertEquals(1687808329L, jsonKey2.getLong("created"));
        assertEquals("S3keySecretByteHere2", jsonKey2.getString("secret"));

        JsonObject jsonKey3 = jsonArray.getJsonObject(2);
        assertEquals(3, jsonKey3.getInteger("id"));
        assertEquals(456, jsonKey3.getInteger("site_id"));
        assertEquals(1687635529L, jsonKey3.getLong("activates"));
        assertEquals(1687808329L, jsonKey3.getLong("created"));
        assertEquals("S3keySecretByteHere3", jsonKey3.getString("secret"));

        assertEquals(extraMeta, metaCaptor.getValue());
    }
}
