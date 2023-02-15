package com.uid2.admin.store.version;

import com.uid2.shared.store.reader.IMetadataVersionedStore;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConsecutiveVersionGeneratorTest {
    @Test
    void incrementsPreviousVersion() throws Exception {
        JsonObject metadata = new JsonObject();
        metadata.put("version", 5);
        IMetadataVersionedStore mockStore = mock(IMetadataVersionedStore.class);
        when(mockStore.getMetadata()).thenReturn(metadata);
        VersionGenerator factory = new ConsecutiveVersionGenerator(mockStore);

        Long actual = factory.getVersion();

        assertEquals(actual, 6);
    }
}