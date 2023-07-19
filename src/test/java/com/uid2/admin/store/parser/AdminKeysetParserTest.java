package com.uid2.admin.store.parser;

import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.auth.AdminKeysetSnapshot;
import com.uid2.admin.model.ClientType;
import com.uid2.shared.store.parser.ParsingResult;
import io.vertx.core.json.JsonArray;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdminKeysetParserTest {

    public static ByteArrayInputStream toInputStream(String data) {
        return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
    }

    public static InputStream makeInputStream(JsonArray content) {
        return toInputStream(content.toString());
    }

    @Test
    public void deserializeKeyset() throws Exception{
        JsonArray keysetArray = new JsonArray();
        List<AdminKeyset> expected = List.of(
                new AdminKeyset(1, 2, "test", Set.of(1, 2, 3), 0, true, true, Set.of(ClientType.DSP)),
                new AdminKeyset(2, 3, "test", Set.of(3), 0, true, true, new HashSet<>()),
                new AdminKeyset(3, 4, "test", Set.of(3), 0, true, true, Set.of(ClientType.DSP, ClientType.ADVERTISER, ClientType.PUBLISHER, ClientType.DATA_PROVIDER))
        );
        for (AdminKeyset keyset : expected) {
            keysetArray.add(keyset);
        }

        AdminKeysetParser parser = new AdminKeysetParser();
        AdminKeysetSnapshot snapshot = parser.deserialize(makeInputStream(keysetArray)).getData();

        Map<Integer, AdminKeyset> results = snapshot.getAllKeysets();

        for (AdminKeyset keyset : expected) {
            AdminKeyset result = results.get(keyset.getKeysetId());
            assertTrue(keyset.equals(result));
        }

    }
}
