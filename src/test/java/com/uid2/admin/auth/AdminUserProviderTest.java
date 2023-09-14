package com.uid2.admin.auth;

import com.uid2.shared.auth.AuthorizableStore;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.cloud.CloudStorageException;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.secret.KeyHashResult;
import com.uid2.shared.secret.KeyHasher;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.impl.OAuth2AuthHandlerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
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
    Vertx vertx = mock(Vertx.class);
    Route route = mock(Route.class);
    ICloudStorage cloudStorage = mock(ICloudStorage.class);
    private static final KeyHasher KEY_HASHER = new KeyHasher();
    private AuthorizableStore<AdminUser> adminUserStore;

    private AdminUser createAdminUser(KeyHashResult khr, String name, String contact) {
        return new AdminUser("", khr.getHash(), khr.getSalt(), name, contact, Instant.now().getEpochSecond(), Set.of(), false);
    }

    @BeforeEach
    public void setup() throws CloudStorageException {
        List<AdminUser> adminUsers = List.of(
                createAdminUser(KEY_HASHER.hashKey("adminUser1"), "adminUser1", "adminUserContact1"),
                createAdminUser(KEY_HASHER.hashKey("adminUser2"), "adminUser2", "adminUserContact2")
        );

        this.adminUserStore = new AuthorizableStore<>(AdminUser.class);
        this.adminUserStore.refresh(adminUsers);
    }

    private JsonObject makeMetadata(String location) {
        JsonObject adminsMetadata = new JsonObject();
        JsonObject admins = new JsonObject();
        admins.put("location", location);
        adminsMetadata.put("admins", admins);
        return adminsMetadata;
    }

    private void addAdminUsers(JsonArray content, List<AdminUser> adminUsers) {
        for (AdminUser adminUser : adminUsers) {
            content.add(adminUser);
        }
    }

    @Test
    public void getAdminUserByContact_returnsAdminUser_withValidAdminUser() throws Exception {
        AdminUserProvider adminUserProvider = new AdminUserProvider(cloudStorage, "metadataPath");

        List<AdminUser> adminUserList = List.of(
                createAdminUser(KEY_HASHER.hashKey("adminUser1"), "adminUser1", "adminUserContact1"),
                createAdminUser(KEY_HASHER.hashKey("adminUser2"), "adminUser2", "adminUserContact2")
        );
        JsonArray content = new JsonArray();
        addAdminUsers(content, adminUserList);
        InputStream inputStream = makeInputStream(content);
        when(cloudStorage.download(anyString())).thenReturn(inputStream);

        adminUserProvider.loadContent(makeMetadata("location"));
        AdminUser result = adminUserProvider.getAdminUserByContact("adminUserContact1");
        assertEquals("adminUser1", result.getName());
    }

    @Test
    public void getAdminUserByContact_returnsNull_withInvalidContact() throws Exception {
        AdminUserProvider adminUserProvider = new AdminUserProvider(cloudStorage, "metadataPath");

        List<AdminUser> adminUserList = List.of(
                createAdminUser(KEY_HASHER.hashKey("adminUser1"), "adminUser1", "adminUserContact1"),
                createAdminUser(KEY_HASHER.hashKey("adminUser2"), "adminUser2", "adminUserContact2")
        );
        JsonArray content = new JsonArray();
        addAdminUsers(content, adminUserList);
        InputStream inputStream = makeInputStream(content);
        when(cloudStorage.download(any())).thenReturn(inputStream);

        adminUserProvider.loadContent(makeMetadata("location"));
        AdminUser result = adminUserProvider.getAdminUserByContact("adminUserContact3");
        assertNull(result);
    }
}
