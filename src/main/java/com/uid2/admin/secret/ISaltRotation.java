package com.uid2.admin.secret;

import com.uid2.shared.store.RotatingSaltProvider;
import lombok.Getter;

import java.time.Duration;

public interface ISaltRotation {
    Result rotateSalts(RotatingSaltProvider.SaltSnapshot lastSnapshot,
                       Duration[] minAges,
                       double fraction,
                       int period) throws Exception;

    @Getter
    class Result {
        private final RotatingSaltProvider.SaltSnapshot snapshot; // can be null if new snapshot is not needed
        private final String reason; // why you are not getting a new snapshot

        private Result(RotatingSaltProvider.SaltSnapshot snapshot, String reason) {
            this.snapshot = snapshot;
            this.reason = reason;
        }

        public boolean hasSnapshot() {
            return snapshot != null;
        }

        public static Result fromSnapshot(RotatingSaltProvider.SaltSnapshot snapshot) {
            return new Result(snapshot, null);
        }

        public static Result noSnapshot(String reason) {
            return new Result(null, reason);
        }
    }
}
