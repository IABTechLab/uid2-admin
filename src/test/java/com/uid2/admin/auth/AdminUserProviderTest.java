package com.uid2.admin.auth;

import com.uid2.shared.auth.AuthorizableStore;
import com.uid2.shared.cloud.CloudStorageException;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.secret.KeyHashResult;
import com.uid2.shared.secret.KeyHasher;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.uid2.admin.TestUtilites.makeInputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminUserProviderTest {
    ICloudStorage cloudStorage = mock(ICloudStorage.class);
    private static final KeyHasher KEY_HASHER = new KeyHasher();
    private List<AdminUser> adminUsers;

    private AdminUser createAdminUser(KeyHashResult khr, String name, String contact) {
        return new AdminUser("", khr.getHash(), khr.getSalt(), name, contact, Instant.now().getEpochSecond(), Set.of(), false);
    }

    @BeforeEach
    public void setup() throws CloudStorageException {
        this.adminUsers = List.of(
                createAdminUser(KEY_HASHER.hashKey("adminUser1"), "adminUser1", "adminUserContact1"),
                createAdminUser(KEY_HASHER.hashKey("adminUser2"), "adminUser2", "adminUserContact2")
        );
    }

    private JsonObject makeMetadata(String location) {
        JsonObject adminsMetadata = new JsonObject();
        JsonObject admins = new JsonObject();
        admins.put("location", location);
        adminsMetadata.put("admins", admins);
        return adminsMetadata;
    }

    private JsonArray createAdminUsers(List<AdminUser> adminUsers) {
        JsonArray content = new JsonArray();
        for (AdminUser adminUser : adminUsers) {
            content.add(adminUser);
        }
        return content;
    }

    @Test
    public void getAdminUserByContact_returnsAdminUser_withValidAdminUser() throws Exception {
        AdminUserProvider adminUserProvider = new AdminUserProvider(cloudStorage, "metadataPath");

        InputStream inputStream = makeInputStream(createAdminUsers(this.adminUsers));
        when(cloudStorage.download("location")).thenReturn(inputStream);

        adminUserProvider.loadContent(makeMetadata("location"));
        AdminUser result = adminUserProvider.getAdminUserByContact("adminUserContact1");
        assertEquals("adminUser1", result.getName());
    }

    @Test
    public void getAdminUserByContact_returnsNull_withInvalidContact() throws Exception {
        AdminUserProvider adminUserProvider = new AdminUserProvider(cloudStorage, "metadataPath");

        InputStream inputStream = makeInputStream(createAdminUsers(this.adminUsers));
        when(cloudStorage.download("location")).thenReturn(inputStream);

        adminUserProvider.loadContent(makeMetadata("location"));
        AdminUser result = adminUserProvider.getAdminUserByContact("adminUserContact3");
        assertNull(result);
    }
}
