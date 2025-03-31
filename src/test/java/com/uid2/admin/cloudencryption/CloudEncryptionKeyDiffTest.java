package com.uid2.admin.cloudencryption;

import com.uid2.shared.model.CloudEncryptionKey;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CloudEncryptionKeyDiffTest {
    private final int siteId = 1;
    private final String secret1 = "secret 1";
    private final CloudEncryptionKey key1 = new CloudEncryptionKey(1, siteId, 0, 0, secret1);
    private final CloudEncryptionKey key2 = new CloudEncryptionKey(2, siteId, 0, 0, secret1);
    private final CloudEncryptionKey key3 = new CloudEncryptionKey(3, siteId, 0, 0, secret1);


    @Test
    void calculateDiff_noKeys() {
        var expected = new CloudEncryptionKeyDiff(0, 0, 0, 0, 0);

        var actual = CloudEncryptionKeyDiff.calculateDiff(Set.of(), Set.of());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void calculateDiff_noChange() {
        var expected = new CloudEncryptionKeyDiff(2, 2, 0, 0, 2);

        var actual = CloudEncryptionKeyDiff.calculateDiff(Set.of(key1, key2), Set.of(key1, key2));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void calculateDiff_keysCreated() {
        var expected = new CloudEncryptionKeyDiff(2, 3, 1, 0, 2);

        var actual = CloudEncryptionKeyDiff.calculateDiff(Set.of(key1, key2), Set.of(key1, key2, key3));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void calculateDiff_keysRemoved() {
        var expected = new CloudEncryptionKeyDiff(3, 1, 0, 2, 1);

        var actual = CloudEncryptionKeyDiff.calculateDiff(Set.of(key1, key2, key3), Set.of(key1));

        assertThat(actual).isEqualTo(expected);
    }
}