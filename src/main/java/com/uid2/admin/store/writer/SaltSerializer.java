package com.uid2.admin.store.writer;

import com.uid2.shared.model.SaltEntry;

public final class SaltSerializer {
    private SaltSerializer() {}

    public static String toCsv(SaltEntry[] entries) {
        StringBuilder stringBuilder = new StringBuilder();

        for (SaltEntry entry : entries) {
            addLine(entry, stringBuilder);
        }

        return stringBuilder.toString();
    }

    private static void addLine(SaltEntry entry, StringBuilder stringBuilder) {
        stringBuilder
                .append(entry.id())
                .append(",")
                .append(entry.lastUpdated())
                .append(",")
                .append(entry.currentSalt());

        stringBuilder.append(",");
        stringBuilder.append(serializeNullable(entry.refreshFrom()));

        stringBuilder.append(",");
        stringBuilder.append(serializeNullable(entry.previousSalt()));

        appendKey(stringBuilder, entry.currentKey());
        appendKey(stringBuilder, entry.previousKey());

        stringBuilder.append("\n");
    }

    private static void appendKey(StringBuilder stringBuilder, SaltEntry.KeyMaterial key) {
        if (key != null) {
            stringBuilder
                    .append(",")
                    .append(key.id())
                    .append(",")
                    .append(serializeNullable(key.key()))
                    .append(",")
                    .append(serializeNullable(key.salt()));
        }
        else  {
            stringBuilder.append(",,,");
        }
    }

    public static <T> String serializeNullable(T obj) {
        return obj == null ? "" : obj.toString();
    }
}
