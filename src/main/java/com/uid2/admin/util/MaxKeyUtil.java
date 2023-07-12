package com.uid2.admin.util;

import com.uid2.shared.model.EncryptionKey;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class MaxKeyUtil {
    public static int getMaxKeyId(Collection<EncryptionKey> inputKeys, Integer oldMaxKeyId) throws Exception {
        final List<EncryptionKey> sortedKeys = inputKeys.stream()
                .sorted(Comparator.comparingInt(EncryptionKey::getId))
                .collect(Collectors.toList());

        int maxKeyId = sortedKeys.isEmpty() ? 0 : sortedKeys.get(sortedKeys.size()-1).getId();
        final Integer metadataMaxKeyId = oldMaxKeyId;
        if(metadataMaxKeyId != null) {
            // allows to avoid re-using deleted keys' ids
            maxKeyId = Integer.max(maxKeyId, metadataMaxKeyId);
        }
        if(maxKeyId == Integer.MAX_VALUE) {
            throw new ArithmeticException("Cannot generate a new key id: max key id reached");
        }

        return maxKeyId;
    }
}
