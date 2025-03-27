package com.uid2.admin.store.writer;

import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.cloud.CloudStorageException;
import com.uid2.shared.cloud.TaggableCloudStorage;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.RotatingSaltProvider;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SaltStoreWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaltStoreWriter.class);
    private final RotatingSaltProvider provider;
    private final FileManager fileManager;
    protected final String saltSnapshotLocationPrefix;
    private final VersionGenerator versionGenerator;

    protected final TaggableCloudStorage cloudStorage;

    private final Map<String, String> currentTags = Map.of("status", "current");
    private final Map<String, String> obsoleteTags = Map.of("status", "obsolete");

    public SaltStoreWriter(JsonObject config, RotatingSaltProvider provider, FileManager fileManager, TaggableCloudStorage cloudStorage, VersionGenerator versionGenerator) {
        this.provider = provider;
        this.fileManager = fileManager;
        this.cloudStorage = cloudStorage;
        this.saltSnapshotLocationPrefix = config.getString("salt_snapshot_location_prefix");
        this.versionGenerator = versionGenerator;
    }

    private List<RotatingSaltProvider.SaltSnapshot> getSnapshots(RotatingSaltProvider.SaltSnapshot data){
        if (provider.getSnapshots() == null) {
            throw new IllegalStateException("Snapshots cannot be null");
        }
        final Instant now = Instant.now();
        List<RotatingSaltProvider.SaltSnapshot> currentSnapshots = provider.getSnapshots();
        List<RotatingSaltProvider.SaltSnapshot> snapshots = null;
        snapshots = Stream.concat(currentSnapshots.stream(), Stream.of(data))
                .sorted(Comparator.comparing(RotatingSaltProvider.SaltSnapshot::getEffective))
                .collect(Collectors.toList());
        RotatingSaltProvider.SaltSnapshot newestEffectiveSnapshot = snapshots.stream()
                .filter(snapshot -> snapshot.isEffective(now))
                .reduce((a, b) -> b).orElse(null);

        List<RotatingSaltProvider.SaltSnapshot> filteredSnapshots = new ArrayList<>();

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
            filteredSnapshots.add(snapshot);
            newestEffectiveSnapshot = null;
        }
        return filteredSnapshots;
    }

    protected JsonObject getMetadata() throws Exception {
        JsonObject metadata = null;
        try {
            metadata = provider.getMetadata();
        } catch (CloudStorageException e) {
            if (e.getMessage().contains("The specified key does not exist")) {
                metadata = new JsonObject();
            } else {
                throw e;
            }
        }
        return metadata;
    }

    protected void buildAndUploadMetadata(JsonObject metadata, JsonArray snapshotsMetadata ) throws Exception{
        final Instant now = Instant.now();
        final long generated = now.getEpochSecond();
        metadata.put("version", versionGenerator.getVersion());
        metadata.put("generated", generated);
        metadata.put("salts", snapshotsMetadata);
        fileManager.uploadMetadata(metadata, "salts", new CloudPath(provider.getMetadataPath()));
    }

    protected JsonArray uploadASnapshotsAndGetMetadata(List<RotatingSaltProvider.SaltSnapshot> snapshots) throws Exception {
        final JsonArray snapshotsMetadata = new JsonArray();
        for (RotatingSaltProvider.SaltSnapshot snapshot : snapshots) {
            final String location = getSaltSnapshotLocation(snapshot);
            final JsonObject snapshotMetadata = new JsonObject();
            snapshotMetadata.put("effective", snapshot.getEffective().toEpochMilli());
            snapshotMetadata.put("expires", snapshot.getExpires().toEpochMilli());
            snapshotMetadata.put("location", location);
            snapshotMetadata.put("size", snapshot.getAllRotatingSalts().length);
            snapshotsMetadata.add(snapshotMetadata);
            uploadSaltsSnapshot(snapshot, location);
        }
        return snapshotsMetadata;
    }

    public void upload(RotatingSaltProvider.SaltSnapshot data) throws Exception {
        JsonObject metadata = this.getMetadata();
        List<RotatingSaltProvider.SaltSnapshot> snapshots = this.getSnapshots(data);
        this.buildAndUploadMetadata(metadata, this.uploadASnapshotsAndGetMetadata(snapshots));
        refreshProvider();
    }

    private void refreshProvider() throws Exception {
        provider.loadContent();
    }

    /**
     * reads the metadata file, and marks each referenced file as ready for archiving
     */
    public void archiveSaltLocations() throws Exception {
        final JsonObject metadata = provider.getMetadata();

        metadata.getJsonArray("salts").forEach(instance -> {
            try {
                JsonObject salt = (JsonObject) instance;
                String location = salt.getString("location", "");
                if (!location.isBlank()) {
                    this.setStatusTagToObsolete(location);
                }
            } catch (Exception ex) {
                LOGGER.error("Error marking object as ready for archiving", ex);
            }
        });
    }

    protected String getSaltSnapshotLocation(RotatingSaltProvider.SaltSnapshot snapshot) {
        return saltSnapshotLocationPrefix + snapshot.getEffective().toEpochMilli();
    }

    protected void uploadSaltsSnapshot(RotatingSaltProvider.SaltSnapshot snapshot, String location) throws Exception {
        // do not overwrite existing files
        if (!cloudStorage.list(location).isEmpty()) {
            // update the tags on the file to ensure it is still marked as current
            this.setStatusTagToCurrent(location);
            return;
        }

        final Path newSaltsFile = Files.createTempFile("operators", ".txt");
        try (BufferedWriter w = Files.newBufferedWriter(newSaltsFile)) {
            for (SaltEntry entry : snapshot.getAllRotatingSalts()) {
                w.write(entry.getId() + "," + entry.getLastUpdated() + "," + entry.getSalt() + "\n");
            }
        }

        this.upload(newSaltsFile.toString(), location);
    }

    protected void upload(String data, String location) throws Exception {
        cloudStorage.upload(data, location, this.currentTags);

    }

    protected void setStatusTagToCurrent(String location) throws CloudStorageException {
        this.cloudStorage.setTags(location, this.currentTags);
    }

    private void setStatusTagToObsolete(String location) throws CloudStorageException {
        this.cloudStorage.setTags(location, this.obsoleteTags);
    }
}
