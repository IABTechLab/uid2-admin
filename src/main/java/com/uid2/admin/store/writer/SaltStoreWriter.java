package com.uid2.admin.store.writer;

import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.CloudPath;
import com.uid2.admin.store.FileName;
import com.uid2.shared.store.RotatingSaltProvider;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SaltStoreWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaltStoreWriter.class);
    private final RotatingSaltProvider provider;
    private final FileManager fileManager;
    private final String saltSnapshotLocationPrefix;
    private final VersionGenerator versionGenerator;

    private final ICloudStorage cloudStorage;

    public SaltStoreWriter(JsonObject config, RotatingSaltProvider provider, FileManager fileManager, ICloudStorage cloudStorage, VersionGenerator versionGenerator) {
        this.provider = provider;
        this.fileManager = fileManager;
        this.cloudStorage = cloudStorage;
        this.saltSnapshotLocationPrefix = config.getString("salt_snapshot_location_prefix");
        this.versionGenerator = versionGenerator;
    }
    public void upload(RotatingSaltProvider.SaltSnapshot data) throws Exception {
        final Instant now = Instant.now();
        final long generated = now.getEpochSecond();

        FileName backupFile = new FileName("salts-old", ".json");

        fileManager.backupFile(new CloudPath(provider.getMetadataPath()), backupFile, generated);

        final JsonObject metadata = provider.getMetadata();
        // bump up metadata version
        metadata.put("version", versionGenerator.getVersion());
        metadata.put("generated", generated);

        final JsonArray snapshotsMetadata = new JsonArray();
        metadata.put("salts", snapshotsMetadata);

        final List<RotatingSaltProvider.SaltSnapshot> snapshots = Stream.concat(provider.getSnapshots().stream(), Stream.of(data))
                .sorted(Comparator.comparing(RotatingSaltProvider.SaltSnapshot::getEffective))
                .collect(Collectors.toList());
        // of the currently effective snapshots keep only the most recent one
        RotatingSaltProvider.SaltSnapshot newestEffectiveSnapshot = snapshots.stream()
                .filter(snapshot -> snapshot.isEffective(now))
                .reduce((a, b) -> b).orElse(null);
        for (RotatingSaltProvider.SaltSnapshot snapshot : snapshots) {
            if (!now.isBefore(snapshot.getExpires())) {
                LOGGER.info("Skipping expired snapshot, effective=" + snapshot.getEffective() + ", expires=" + snapshot.getExpires());
                continue;
            }

            if (newestEffectiveSnapshot != null && snapshot != newestEffectiveSnapshot) {
                LOGGER.info("Skipping effective snapshot, effective=" + snapshot.getEffective() + ", expires=" + snapshot.getExpires()
                        + " in favour of newer snapshot, effective=" + newestEffectiveSnapshot.getEffective() + ", expires=" + newestEffectiveSnapshot.getExpires());
                continue;
            }
            newestEffectiveSnapshot = null;

            final String location = getSaltSnapshotLocation(snapshot);

            final JsonObject snapshotMetadata = new JsonObject();
            snapshotMetadata.put("effective", snapshot.getEffective().toEpochMilli());
            snapshotMetadata.put("expires", snapshot.getExpires().toEpochMilli());
            snapshotMetadata.put("location", location);
            snapshotMetadata.put("size", snapshot.getAllRotatingSalts().length);
            snapshotsMetadata.add(snapshotMetadata);

            uploadSaltsSnapshot(snapshot, location);
        }

        fileManager.uploadMetadata(metadata, "salts", new CloudPath(provider.getMetadataPath()));

        // refresh manually
        provider.loadContent();
    }

    private String getSaltSnapshotLocation(RotatingSaltProvider.SaltSnapshot snapshot) {
        return saltSnapshotLocationPrefix + snapshot.getEffective().toEpochMilli();
    }

    private void uploadSaltsSnapshot(RotatingSaltProvider.SaltSnapshot snapshot, String location) throws Exception {
        // do not overwrite existing files
        if (!cloudStorage.list(location).isEmpty()) return;

        final Path newSaltsFile = Files.createTempFile("operators", ".txt");
        try (BufferedWriter w = Files.newBufferedWriter(newSaltsFile)) {
            for (SaltEntry entry : snapshot.getAllRotatingSalts()) {
                w.write(entry.getId() + "," + entry.getLastUpdated() + "," + entry.getSalt() + "\n");
            }
        }

        cloudStorage.upload(newSaltsFile.toString(), location);
    }
}
