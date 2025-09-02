package com.uid2.admin.store.writer;

import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.model.SaltEntry.KeyMaterial;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class SaltSerializerTest {
    @Test
    void toCsv_serializesNoSalts() {
        var expected = "";

        var salts = new SaltEntry[]{};
        var actual = SaltSerializer.toCsv(salts);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void toCsv_serializesSaltsWithNoOptionalFields() {
        var expected = """
1,100,salt1,,,,,,,,
2,200,salt2,,,,,,,,
""";

        var salts = new SaltEntry[]{
                new SaltEntry(1, "hashedId1", 100, "salt1", 0, null, null, null),
                new SaltEntry(2, "hashedId2", 200, "salt2", 0, null, null, null),
        };
        var actual = SaltSerializer.toCsv(salts);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void toCsv_serializesSaltsWithAllOptionalFields() {
        var expected = """
1,100,salt1,1000,previousSalt1,11,key1,keySalt1,111,key11,keySalt11
2,200,salt2,2000,previousSalt2,22,key2,keySalt2,222,key22,keySalt22
""";

        var salts = new SaltEntry[]{
                new SaltEntry(1,
                        "hashedId1",
                        100,
                        "salt1",
                        1000L,
                        "previousSalt1",
                        new KeyMaterial(11, "key1", "keySalt1"),
                        new KeyMaterial(111, "key11", "keySalt11")
                ),
                new SaltEntry(2,
                        "hashedId2",
                        200,
                        "salt2",
                        2000L,
                        "previousSalt2",
                        new KeyMaterial(22, "key2", "keySalt2"),
                        new KeyMaterial(222, "key22", "keySalt22")
                ),
        };
        var actual = SaltSerializer.toCsv(salts);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void toCsv_serializesSaltsWithV3IdentityMapFields() {
        var expected = """
1,100,salt1,1000,previousSalt1,,,,,,
2,200,salt2,2000,previousSalt2,,,,,,
""";

        var salts = new SaltEntry[]{
                new SaltEntry(1,
                        "hashedId1",
                        100,
                        "salt1",
                        1000L,
                        "previousSalt1",
                        null,
                        null
                ),
                new SaltEntry(2,
                        "hashedId2",
                        200,
                        "salt2",
                        2000L,
                        "previousSalt2",
                        null,
                        null
                ),
        };
        var actual = SaltSerializer.toCsv(salts);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void toCsv_toleratesNullsInKeys() {
        var expected = """
1,100,salt1,1000,previousSalt1,11,,,111,,
""";

        var salts = new SaltEntry[]{
                new SaltEntry(1,
                        "hashedId1",
                        100,
                        "salt1",
                        1000L,
                        "previousSalt1",
                        new KeyMaterial(11, null, null),
                        new KeyMaterial(111, null, null)
                ),
        };
        var actual = SaltSerializer.toCsv(salts);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void toCsv_mixedNullsInKeysAndSalts() {
        var expected = """
1,100,,1000,previousSalt,0,currentKeyKey,currentKeySalt,,,
2,200,,2000,,0,currentKeyKey,currentKeySalt,1,previousKeyKey,previousKeySalt
3,300,salt,3000,,,,,1,previousKeyKey,previousKeySalt
4,400,,4000,,0,currentKeyKey,currentKeySalt,,,
""";
        var currentKey = new KeyMaterial(0, "currentKeyKey", "currentKeySalt");
        var previousKey = new KeyMaterial(1, "previousKeyKey", "previousKeySalt");

        var salts = new SaltEntry[]{
            new SaltEntry(1, "hashedId1", 100, null, 1000L, "previousSalt", currentKey, null),
            new SaltEntry(2, "hashedId2", 200, null, 2000L, null, currentKey, previousKey),
            new SaltEntry(3, "hashedId3", 300, "salt", 3000L, null, null, previousKey),
            new SaltEntry(4, "hashedId4", 400, null, 4000L, null, currentKey, null),
        };
        var actual = SaltSerializer.toCsv(salts);

        assertThat(actual).isEqualTo(expected);
    }
}