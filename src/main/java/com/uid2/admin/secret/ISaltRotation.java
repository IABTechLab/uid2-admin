// Copyright (c) 2021 The Trade Desk, Inc
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package com.uid2.admin.secret;

import com.uid2.shared.store.RotatingSaltProvider;

import java.time.Duration;
import java.util.List;

public interface ISaltRotation {
    Result rotateSalts(RotatingSaltProvider.SaltSnapshot lastSnapshot,
                       Duration[] minAges,
                       double fraction) throws Exception;

    class Result {
        private RotatingSaltProvider.SaltSnapshot snapshot; // can be null if new snapshot is not needed
        private List<Integer> rotationIndices; // the indices whose salts were rotated
        private String reason; // why you are not getting a new snapshot

        private Result(RotatingSaltProvider.SaltSnapshot snapshot, List<Integer> rotationIndices, String reason) {
            this.snapshot = snapshot;
            this.rotationIndices = rotationIndices;
            this.reason = reason;
        }

        public boolean hasSnapshot() { return snapshot != null; }
        public RotatingSaltProvider.SaltSnapshot getSnapshot() { return snapshot; }
        public List<Integer> getRotationIndices() { return rotationIndices; }
        public String getReason() { return reason; }

        public static Result fromSnapshot(RotatingSaltProvider.SaltSnapshot snapshot) {
            return new Result(snapshot, null, null);
        }
        public static Result fromSnapshot(RotatingSaltProvider.SaltSnapshot snapshot, List<Integer> rotationIndices) {
            return new Result(snapshot, rotationIndices, null);
        }
        public static Result noSnapshot(String reason) {
            return new Result(null, null, reason);
        }
    }
}
