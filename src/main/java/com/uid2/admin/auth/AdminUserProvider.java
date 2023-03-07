package com.uid2.admin.auth;

import com.uid2.shared.Utils;
import com.uid2.shared.auth.IAuthorizable;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.reader.IMetadataVersionedStore;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class AdminUserProvider implements IAdminUserProvider, IMetadataVersionedStore {
    public static final String ADMINS_METADATA_PATH = "admins_metadata_path";

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminUserProvider.class);

    private final ICloudStorage metadataStreamProvider;
    private final ICloudStorage contentStreamProvider;
    private final String metadataPath;
    private final AtomicReference<Map<String, AdminUser>> latestSnapshot = new AtomicReference<Map<String, AdminUser>>(null);

    private final AtomicReference<Map<String, AdminUser>> latestSnapshotByContact = new AtomicReference<Map<String, AdminUser>>(null);

    public AdminUserProvider(ICloudStorage cloudStorage, String metadataPath) {
        this.metadataStreamProvider = cloudStorage;
        this.contentStreamProvider = cloudStorage;
        this.metadataPath = metadataPath;
    }

    public String getMetadataPath() {
        return metadataPath;
    }

    @Override
    public JsonObject getMetadata() throws Exception {
        InputStream s = this.metadataStreamProvider.download(this.metadataPath);
        return Utils.toJsonObject(s);
    }

    @Override
    public long getVersion(JsonObject metadata) {
        return metadata.getLong("version");
    }

    @Override
    public long loadContent(JsonObject metadata) throws Exception {
        final JsonObject adminsMetadata = metadata.getJsonObject("admins");
        final String contentPath = adminsMetadata.getString("location");
        final InputStream contentStream = this.contentStreamProvider.download(contentPath);
        return loadAdmins(contentStream);
    }

    private long loadAdmins(InputStream contentStream) throws Exception {
        JsonArray adminUsers = Utils.toJsonArray(contentStream);
        Map<String, AdminUser> keyMap = new HashMap<>();
        Map<String, AdminUser> contactMap = new HashMap<>();
        for (int i = 0; i < adminUsers.size(); ++i){
            JsonObject spec = adminUsers.getJsonObject(i);
            AdminUser adminUser = AdminUser.valueOf(spec);
            keyMap.put(adminUser.getKey(), adminUser);
            contactMap.put(adminUser.getContact(), adminUser);
        }
        this.latestSnapshot.set(keyMap);
        this.latestSnapshotByContact.set(contactMap);

        LOGGER.info("Loaded " + keyMap.size() + " admin user profiles");
        return keyMap.size();
    }

    @Override
    public AdminUser getAdminUser(String token) {
        return this.latestSnapshot.get().get(token);
    }

    @Override
    public Collection<AdminUser> getAll() {
        return this.latestSnapshot.get().values();
    }

    @Override
    public AdminUser getAdminUserByContact(String contact) {
        return this.latestSnapshotByContact.get().get(contact);
    }

    @Override
    public IAuthorizable get(String key) {
        return getAdminUser(key);
    }
}
