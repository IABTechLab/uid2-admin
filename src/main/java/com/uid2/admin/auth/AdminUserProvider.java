package com.uid2.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.uid2.shared.Utils;
import com.uid2.shared.auth.AuthorizableStore;
import com.uid2.shared.auth.IAuthorizable;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.reader.IMetadataVersionedStore;
import com.uid2.shared.utils.ObjectMapperFactory;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class AdminUserProvider implements IAdminUserProvider, IMetadataVersionedStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminUserProvider.class);
    public static final String ADMINS_METADATA_PATH = "admins_metadata_path";
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.build();

    private final ICloudStorage metadataStreamProvider;
    private final ICloudStorage contentStreamProvider;
    private final String metadataPath;
    private final AtomicReference<Map<String, AdminUser>> latestSnapshotByContact;
    private final AuthorizableStore<AdminUser> adminUserStore;

    public AdminUserProvider(ICloudStorage cloudStorage, String metadataPath) {
        this.latestSnapshotByContact = new AtomicReference<>(new HashMap<>());
        this.metadataStreamProvider = cloudStorage;
        this.contentStreamProvider = cloudStorage;
        this.metadataPath = metadataPath;
        this.adminUserStore = new AuthorizableStore<>(AdminUser.class);
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
        String adminUsersJson = CharStreams.toString(new InputStreamReader(contentStream, Charsets.UTF_8));
        List<AdminUser> adminUsers = Arrays.asList(OBJECT_MAPPER.readValue(adminUsersJson, AdminUser[].class));
        adminUserStore.refresh(adminUsers);
        LOGGER.info("Loaded " + adminUsers.size() + " operator profiles");
        return adminUsers.size();
    }

    @Override
    public AdminUser getAdminUser(String token) {
        return adminUserStore.getAuthorizableByKey(token);
    }

    @Override
    public Collection<AdminUser> getAll() {
        return adminUserStore.getAuthorizables();
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
