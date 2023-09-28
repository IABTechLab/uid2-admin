package com.uid2.admin.legacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.shared.store.parser.Parser;
import com.uid2.shared.store.parser.ParsingResult;
import com.uid2.shared.util.Mapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

public class LegacyClientParser implements Parser<Collection<LegacyClientKey>> {
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();

    @Override
    public ParsingResult<Collection<LegacyClientKey>> deserialize(InputStream inputStream) throws IOException {
        LegacyClientKey[] clientKeys = OBJECT_MAPPER.readValue(inputStream, LegacyClientKey[].class);
        return new ParsingResult<>(Arrays.asList(clientKeys), clientKeys.length);
    }
}
