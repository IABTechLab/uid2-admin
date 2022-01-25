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

package com.uid2.admin.store;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.auth.AdminUserProvider;
import com.uid2.admin.model.Site;
import com.uid2.shared.auth.*;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.EnclaveIdentifier;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.RotatingKeyStore;
import com.uid2.shared.store.RotatingSaltProvider;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CloudStorageManager implements IStorageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CloudStorageManager.class);

    private ICloudStorage cloudStorage;
    private final String saltSnapshotLocationPrefix;
    private final ObjectWriter jsonWriter;

    public CloudStorageManager(JsonObject config, ICloudStorage cloudStorage) {
        this.cloudStorage = cloudStorage;

        saltSnapshotLocationPrefix = config.getString("salt_snapshot_location_prefix");

        ObjectMapper mapper = new ObjectMapper();
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        this.jsonWriter = mapper.writer(pp);
    }

    @Override
    public void uploadSites(RotatingSiteStore provider, Collection<Site> sites) throws Exception {
        final long generated = Instant.now().getEpochSecond();

        final JsonObject metadata = provider.getMetadata();
        // bump up metadata version
        metadata.put("version", metadata.getLong("version") + 1);
        metadata.put("generated", generated);

        // get location to upload
        final String location = metadata.getJsonObject("sites").getString("location");

        backupFile(location, "sites-old", ".json", generated);

        // generate new sites
        final Path newSitesFile = Files.createTempFile("sites", ".json");
        final byte[] contentBytes = jsonWriter.writeValueAsString(sites).getBytes(StandardCharsets.UTF_8);
        Files.write(newSitesFile, contentBytes, StandardOpenOption.CREATE);

        // upload new sites
        cloudStorage.upload(newSitesFile.toString(), location);
        uploadMetadata(metadata, "sites", provider.getMetadataPath());

        // refresh manually
        provider.loadContent();    }

    @Override
    public void uploadClientKeys(RotatingClientKeyProvider provider, Collection<ClientKey> clients) throws Exception {
        long generated = Instant.now().getEpochSecond();

        JsonObject metadata = provider.getMetadata();
        // bump up metadata version
        metadata.put("version", metadata.getLong("version") + 1);
        metadata.put("generated", generated);

        // get location to upload
        String location = metadata.getJsonObject("client_keys").getString("location");

        backupFile(location, "clients-old", ".json", generated);

        // generate new clients
        Path newClientsFile = Files.createTempFile("clients", ".json");
        byte[] contentBytes = jsonWriter.writeValueAsString(clients).getBytes(StandardCharsets.UTF_8);
        Files.write(newClientsFile, contentBytes, StandardOpenOption.CREATE);

        // upload new clients
        cloudStorage.upload(newClientsFile.toString(), location);
        uploadMetadata(metadata, "clients", provider.getMetadataPath());

        // refresh manually
        provider.loadContent(provider.getMetadata());
    }

    @Override
    public void uploadEncryptionKeys(RotatingKeyStore provider, Collection<EncryptionKey> keys, Integer newMaxKeyId) throws Exception {
        final long generated = Instant.now().getEpochSecond();
        final JsonObject metadata = provider.getMetadata();

        metadata.put("version", metadata.getLong("version") + 1);
        metadata.put("generated", generated);
        if (newMaxKeyId != null) {
            metadata.put("max_key_id", newMaxKeyId);
        }
        final String location = metadata.getJsonObject("keys").getString("location");

        backupFile(location, "keys-old", ".json", generated);

        // generate new keys
        final Path newFile = Files.createTempFile("keys", ".json");
        final JsonArray jsonKeys = new JsonArray();
        for (EncryptionKey key : keys) {
            JsonObject json = new JsonObject();
            json.put("id", key.getId());
            json.put("site_id", key.getSiteId());
            json.put("created", key.getCreated().getEpochSecond());
            json.put("activates", key.getActivates().getEpochSecond());
            json.put("expires", key.getExpires().getEpochSecond());
            json.put("secret", key.getKeyBytes());
            jsonKeys.add(json);
        }
        final byte[] contentBytes = jsonKeys.encodePrettily().getBytes(StandardCharsets.UTF_8);
        Files.write(newFile, contentBytes, StandardOpenOption.CREATE);

        // upload new keys
        cloudStorage.upload(newFile.toString(), location);
        uploadMetadata(metadata, "keys", provider.getMetadataPath());

        // refresh manually
        provider.loadContent();
    }

    @Override
    public void uploadKeyAcls(RotatingKeyAclProvider provider, Map<Integer, EncryptionKeyAcl> acls) throws Exception {
        final long generated = Instant.now().getEpochSecond();

        final JsonObject metadata = provider.getMetadata();
        // bump up metadata version
        metadata.put("version", metadata.getLong("version") + 1);
        metadata.put("generated", generated);

        // get location to upload
        final String location = metadata.getJsonObject("keys_acl").getString("location");

        backupFile(location, "keys_acl-old", ".json", generated);

        // generate new acls
        Path newFile = Files.createTempFile("keys_acl", ".json");
        JsonArray jsonAcls = new JsonArray();
        for(Map.Entry<Integer, EncryptionKeyAcl> acl : acls.entrySet()) {
            JsonObject jsonAcl = new JsonObject();
            jsonAcl.put("site_id", acl.getKey());
            jsonAcl.put((acl.getValue().getIsWhitelist() ? "whitelist" : "blacklist"),
                    new JsonArray(new ArrayList<>(acl.getValue().getAccessList())));
            jsonAcls.add(jsonAcl);
        }
        byte[] contentBytes = jsonAcls.encodePrettily().getBytes(StandardCharsets.UTF_8);
        Files.write(newFile, contentBytes, StandardOpenOption.CREATE);

        // upload new sites
        cloudStorage.upload(newFile.toString(), location);
        uploadMetadata(metadata, "keys_acl", provider.getMetadataPath());

        // refresh manually
        provider.loadContent();
    }

    @Override
    public void uploadSalts(RotatingSaltProvider provider, RotatingSaltProvider.SaltSnapshot newSnapshot) throws Exception {
        final Instant now = Instant.now();
        final long generated = now.getEpochSecond();

        backupFile(provider.getMetadataPath(), "salts-old", ".json", generated);

        final JsonObject metadata = provider.getMetadata();
        // bump up metadata version
        metadata.put("version", metadata.getLong("version") + 1);
        metadata.put("generated", generated);

        final JsonArray snapshotsMetadata = new JsonArray();
        metadata.put("salts", snapshotsMetadata);

        final List<RotatingSaltProvider.SaltSnapshot> snapshots = Stream.concat(provider.getSnapshots().stream(), Stream.of(newSnapshot))
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

        uploadMetadata(metadata, "salts", provider.getMetadataPath());

        // refresh manually
        provider.loadContent();
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

    @Override
    public void uploadOperatorKeys(RotatingOperatorKeyProvider provider, Collection<OperatorKey> operators) throws Exception {
        long generated = Instant.now().getEpochSecond();

        JsonObject metadata = provider.getMetadata();
        // bump up metadata version
        metadata.put("version", metadata.getLong("version") + 1);
        metadata.put("generated", generated);

        // get location to upload
        String location = metadata.getJsonObject("operators").getString("location");

        backupFile(location, "operators-old", ".json", generated);

        // generate new operators
        Path newOperatorsFile = Files.createTempFile("operators", ".json");
        byte[] contentBytes = jsonWriter.writeValueAsString(operators).getBytes(StandardCharsets.UTF_8);
        Files.write(newOperatorsFile, contentBytes, StandardOpenOption.CREATE);

        // upload new operators
        cloudStorage.upload(newOperatorsFile.toString(), location);
        uploadMetadata(metadata, "operators", provider.getMetadataPath());

        // refresh manually
        provider.loadContent(provider.getMetadata());
    }

    @Override
    public void uploadEnclaveIds(EnclaveIdentifierProvider provider, Collection<EnclaveIdentifier> identifiers) throws Exception {
        long generated = Instant.now().getEpochSecond();
        JsonObject metadata = provider.getMetadata();

        metadata.put("version", metadata.getLong("version") + 1);
        metadata.put("generated", generated);
        String location = metadata.getJsonObject("enclaves").getString("location");

        backupFile(location, "enclaves-old", ".json", generated);

        // generate new clients
        Path newFile = Files.createTempFile("enclaves", ".json");
        byte[] contentBytes = jsonWriter.writeValueAsString(identifiers).getBytes(StandardCharsets.UTF_8);
        Files.write(newFile, contentBytes, StandardOpenOption.CREATE);

        // upload new enclaves
        cloudStorage.upload(newFile.toString(), location);
        uploadMetadata(metadata, "enclaves", provider.getMetadataPath());

        // refresh manually
        provider.loadContent(provider.getMetadata());
    }

    @Override
    public void uploadAdminUsers(AdminUserProvider provider, Collection<AdminUser> admins) throws Exception {
        long generated = Instant.now().getEpochSecond();

        JsonObject metadata = provider.getMetadata();
        // bump up metadata version
        metadata.put("version", metadata.getLong("version") + 1);
        metadata.put("generated", generated);

        // get location to upload
        String location = metadata.getJsonObject("admins").getString("location");

        backupFile(location, "admins-old", ".json", generated);

        // generate new admins
        Path newAdminsFile = Files.createTempFile("admins", ".json");
        byte[] contentBytes = jsonWriter.writeValueAsString(admins).getBytes(StandardCharsets.UTF_8);
        Files.write(newAdminsFile, contentBytes, StandardOpenOption.CREATE);

        // upload new admins
        cloudStorage.upload(newAdminsFile.toString(), location);
        uploadMetadata(metadata, "admins", provider.getMetadataPath());

        // refresh manually
        provider.loadContent(provider.getMetadata());
    }

    private void backupFile(String path, String name, String suffix, long timestamp) throws Exception {
        final Path localTemp = Files.createTempFile(name, suffix);
        Files.copy(cloudStorage.download(path), localTemp, StandardCopyOption.REPLACE_EXISTING);
        cloudStorage.upload(localTemp.toString(), path + ".bak");
        cloudStorage.upload(localTemp.toString(), path + "." + timestamp + ".bak");
    }

    private void uploadMetadata(JsonObject metadata, String name, String location) throws Exception {
        final Path newMetadataFile = Files.createTempFile(name + "-metadata", ".json");
        final byte[] mdBytes = Json.encodePrettily(metadata).getBytes(StandardCharsets.UTF_8);
        Files.write(newMetadataFile, mdBytes, StandardOpenOption.CREATE);
        cloudStorage.upload(newMetadataFile.toString(), location);
    }

    private String getSaltSnapshotLocation(RotatingSaltProvider.SaltSnapshot snapshot) {
        return saltSnapshotLocationPrefix + snapshot.getEffective().toEpochMilli();
    }
}
