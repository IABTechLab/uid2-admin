package com.uid2.admin.salt.helper;

import com.uid2.admin.salt.SaltRotation;
import com.uid2.admin.salt.TargetDate;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.salt.RotatingSaltProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.uid2.admin.salt.helper.TargetDateUtil.daysEarlier;
import static com.uid2.admin.salt.helper.TargetDateUtil.daysLater;

public class SaltSnapshotBuilder {
    private final List<SaltEntry> entries = new ArrayList<>();

    private TargetDate effective = daysEarlier(1);
    private TargetDate expires = daysLater(6);

    private SaltSnapshotBuilder() {
    }

    public static SaltSnapshotBuilder start() {
        return new SaltSnapshotBuilder();
    }

    public SaltSnapshotBuilder entries(int count, TargetDate lastUpdated) {
        for (int i = 0; i < count; ++i) {
            entries.add(SaltBuilder.start().lastUpdated(lastUpdated).build());
        }
        return this;
    }

    public SaltSnapshotBuilder entries(SaltBuilder... salts) {
        SaltEntry[] builtSalts = Arrays.stream(salts).map(SaltBuilder::build).toArray(SaltEntry[]::new);
        Collections.addAll(this.entries, builtSalts);
        return this;
    }

    public SaltSnapshotBuilder effective(TargetDate effective) {
        this.effective = effective;
        return this;
    }

    public SaltSnapshotBuilder expires(TargetDate expires) {
        this.expires = expires;
        return this;
    }

    public RotatingSaltProvider.SaltSnapshot build() {
        return new RotatingSaltProvider.SaltSnapshot(
                effective.asInstant(),
                expires.asInstant(),
                entries.toArray(SaltEntry[]::new),
                "test_first_level_salt"
        );
    }
}
