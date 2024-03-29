package com.uid2.admin.vertx;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uid2.shared.util.Mapper;

public class JsonUtil {
    private static final ObjectWriter INSTANCE;

    static {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

        INSTANCE = Mapper.getInstance()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .writer(pp);
    }

    private JsonUtil() {
    }

    public static ObjectWriter createJsonWriter() {
        return INSTANCE;
    }
}
