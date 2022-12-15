package com.uid2.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.shared.Utils;
import com.uid2.shared.auth.IAuthorizable;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.IMetadataVersionedStore;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SpringAdminUserProvider implements IAdminUserProvider, IMetadataVersionedStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAdminUserProvider.class);
    private static final String ADMINS_METADATA_PATH = "admins_metadata_path";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ICloudStorage metadataStreamProvider;
    private final ICloudStorage contentStreamProvider;

    private final AtomicReference<Map<String, AdminUser>> latestSnapshot = new AtomicReference<>();
    private final AtomicReference<Map<String, AdminUser>> latestSnapshotByContact = new AtomicReference<>();

    public SpringAdminUserProvider(ICloudStorage cloudStorage) {
        this.metadataStreamProvider = cloudStorage;
        this.contentStreamProvider = cloudStorage;
    }

    @Override
    public JsonObject getMetadata() throws Exception {
        final InputStream s = metadataStreamProvider.download(ADMINS_METADATA_PATH);
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
        final InputStream contentStream = contentStreamProvider.download(contentPath);
        return loadAdmins(contentStream);
    }

    private long loadAdmins(InputStream contentStream) throws Exception {
        final AdminUser[] adminUsers = OBJECT_MAPPER.readValue(contentStream, AdminUser[].class);

        final Map<String, AdminUser> keyMap = new HashMap<>();
        final Map<String, AdminUser> contactMap = new HashMap<>();
        for (AdminUser adminUser : adminUsers) {
            keyMap.put(adminUser.getKey(), adminUser);
            contactMap.put(adminUser.getContact(), adminUser);
        }
        latestSnapshot.set(keyMap);
        latestSnapshotByContact.set(contactMap);

        LOGGER.info("Loaded {} admin user profiles", keyMap.size());
        return keyMap.size();
    }

    @Override
    public AdminUser getAdminUser(String token) {
        return latestSnapshot.get().get(token);
    }

    @Override
    public Collection<AdminUser> getAll() {
        return latestSnapshot.get().values();
    }

    @Override
    public AdminUser getAdminUserByContact(String contact) {
        return latestSnapshotByContact.get().get(contact);
    }

    @Override
    public IAuthorizable get(String key) {
        return getAdminUser(key);
    }

}
