package com.uid2.admin.vertx.test;

import com.uid2.admin.auth.*;
import com.uid2.admin.managers.KeysetManager;
import com.uid2.admin.secret.IEncryptionKeyManager;
import com.uid2.shared.model.*;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.admin.secret.IKeysetKeyManager;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.reader.RotatingAdminKeysetStore;
import com.uid2.admin.store.writer.*;
import com.uid2.admin.vertx.AdminVerticle;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.admin.vertx.service.IService;
import com.uid2.shared.Const;
import com.uid2.shared.Utils;
import com.uid2.shared.auth.*;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.secret.KeyHashResult;
import com.uid2.shared.secret.KeyHasher;
import com.uid2.shared.store.ClientSideKeypairStoreSnapshot;
import com.uid2.shared.store.IKeyStore;
import com.uid2.shared.store.IKeysetKeyStore;
import com.uid2.shared.store.KeysetKeyStoreSnapshot;
import com.uid2.shared.store.reader.*;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public abstract class ServiceTestBase {
    protected AutoCloseable mocks;
    protected final JsonObject config = new JsonObject();
    protected final WriteLock writeLock = new WriteLock();
    protected AuthMiddleware auth;

    @Mock protected AuthenticationHandler authHandler;
    @Mock protected AuthFactory authFactory;

    @Mock protected FileManager fileManager;
    @Mock protected AdminUserStoreWriter adminUserStoreWriter;
    @Mock protected StoreWriter storeWriter;
    @Mock protected ClientKeyStoreWriter clientKeyStoreWriter;
    @Mock protected EncryptionKeyStoreWriter encryptionKeyStoreWriter;
    @Mock protected KeysetKeyStoreWriter keysetKeyStoreWriter;
    @Mock protected ClientSideKeypairStoreWriter keypairStoreWriter;
    @Mock protected ServiceStoreWriter serviceStoreWriter;
    @Mock protected ServiceLinkStoreWriter serviceLinkStoreWriter;
    @Mock protected KeyAclStoreWriter keyAclStoreWriter;
    @Mock protected KeysetStoreWriter keysetStoreWriter;
    @Mock protected AdminKeysetWriter adminKeysetWriter;
    @Mock protected OperatorKeyStoreWriter operatorKeyStoreWriter;
    @Mock protected EnclaveStoreWriter enclaveStoreWriter;
    @Mock protected SaltStoreWriter saltStoreWriter;
    @Mock protected PartnerStoreWriter partnerStoreWriter;

    @Mock protected IEncryptionKeyManager keyManager;
    @Mock protected KeysetManager keysetManager;
    @Mock protected IKeysetKeyManager keysetKeyManager;
    @Mock protected AdminUserProvider adminUserProvider;
    @Mock protected RotatingSiteStore siteProvider;
    @Mock protected RotatingClientKeyProvider clientKeyProvider;
    @Mock protected RotatingKeyStore keyProvider;
    @Mock protected IKeyStore.IKeyStoreSnapshot keyProviderSnapshot;
    @Mock protected KeysetKeyStoreSnapshot keysetKeyProviderSnapshot;
    @Mock protected RotatingKeyAclProvider keyAclProvider;
    @Mock protected RotatingKeysetProvider keysetProvider;
    @Mock protected RotatingAdminKeysetStore adminKeysetProvider;
    @Mock protected RotatingKeysetKeyStore keysetKeyProvider;
    @Mock protected RotatingClientSideKeypairStore keypairProvider;
    @Mock protected RotatingServiceStore serviceProvider;
    @Mock protected RotatingServiceLinkStore serviceLinkProvider;
    @Mock protected KeysetSnapshot keysetSnapshot;
    @Mock protected AdminKeysetSnapshot adminKeysetSnapshot;
    @Mock protected AclSnapshot keyAclProviderSnapshot;
    @Mock protected RotatingOperatorKeyProvider operatorKeyProvider;
    @Mock protected EnclaveIdentifierProvider enclaveIdentifierProvider;
    @Mock protected IKeyGenerator keyGenerator;
    @Mock protected KeyHasher keyHasher;

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext testContext) throws Throwable {
        mocks = MockitoAnnotations.openMocks(this);
        when(authFactory.createAuthHandler(any(), any(), any())).thenReturn(authHandler);
        when(keyProvider.getSnapshot()).thenReturn(keyProviderSnapshot);
        when(keysetKeyProvider.getSnapshot()).thenReturn(keysetKeyProviderSnapshot);
        when(keyAclProvider.getSnapshot()).thenReturn(keyAclProviderSnapshot);
        when(keysetProvider.getSnapshot()).thenReturn(keysetSnapshot);
        when(adminKeysetProvider.getSnapshot()).thenReturn(adminKeysetSnapshot);
        when(siteProvider.getSite(anyInt())).then((i) -> siteProvider.getAllSites().stream()
                .filter(s -> s.getId() == (Integer) i.getArgument(0)).findFirst().orElse(null));
        when(keyGenerator.generateRandomKey(anyInt())).thenReturn(new byte[]{1, 2, 3, 4, 5, 6});
        when(keyGenerator.generateRandomKeyString(anyInt())).thenReturn(Utils.toBase64String(new byte[]{1, 2, 3, 4, 5, 6}));
        when(keyGenerator.generateFormattedKeyString(anyInt())).thenReturn("abcdef.abcdefabcdefabcdef");
        when(keyHasher.hashKey(anyString())).thenReturn(new KeyHashResult("abcdefabcdefabcdefabcdef", "ghijklghijklghijklghijkl"));

        auth = new AuthMiddleware(this.adminUserProvider);
        IService[] services = {createService()};
        AdminVerticle verticle = new AdminVerticle(config, authFactory, adminUserProvider, services);
        vertx.deployVerticle(verticle, testContext.succeeding(id -> testContext.completeNow()));
    }

    @AfterEach
    public void teardown() throws Exception {
        mocks.close();
    }

    protected abstract IService createService();

    private String getUrlForEndpoint(String endpoint) {
        return String.format("http://127.0.0.1:%d/%s", Const.Port.ServicePortForAdmin + Utils.getPortOffset(), endpoint);
    }

    protected void fakeAuth(Role... roles) {
        AdminUser adminUser = new AdminUser(null, null, null, null, null, 0, new HashSet<>(Arrays.asList(roles)), false);
        when(adminUserProvider.get(any())).thenReturn(adminUser);
    }

    protected void get(Vertx vertx, String endpoint, Handler<AsyncResult<HttpResponse<Buffer>>> handler) {
        WebClient client = WebClient.create(vertx);
        client.getAbs(getUrlForEndpoint(endpoint)).send(handler);
    }

    protected void post(Vertx vertx, String endpoint, String body, Handler<AsyncResult<HttpResponse<Buffer>>> handler) {
        WebClient client = WebClient.create(vertx);
        client.postAbs(getUrlForEndpoint(endpoint)).sendBuffer(Buffer.buffer(body), handler);
    }

    protected void postWithoutBody(Vertx vertx, String endpoint, Handler<AsyncResult<HttpResponse<Buffer>>> handler) {
        WebClient client = WebClient.create(vertx);
        client.postAbs(getUrlForEndpoint(endpoint)).sendBuffer(null, handler);
    }

    protected void setServices(Service... services) {
        when(serviceProvider.getAllServices()).thenReturn(Arrays.asList(services));
        for (Service s : services) {
            when(serviceProvider.getService(eq(s.getServiceId()))).thenReturn(s);
        }
    }

    protected void setServiceLinks(ServiceLink... serviceLinks) {
        when(serviceLinkProvider.getAllServiceLinks()).thenReturn(Arrays.asList(serviceLinks));
    }

    protected void setSites(Site... sites) {
        when(siteProvider.getAllSites()).thenReturn(Arrays.asList(sites));
    }

    protected void setClientKeys(ClientKey... clientKeys) {
        when(clientKeyProvider.getAll()).thenReturn(Arrays.asList(clientKeys));
    }


    protected void setEncryptionKeys(int maxKeyId, EncryptionKey... keys) throws Exception {
        JsonObject metadata = new JsonObject();
        metadata.put("max_key_id", maxKeyId);
        when(keyProvider.getMetadata()).thenReturn(metadata);
        when(keyProviderSnapshot.getActiveKeySet()).thenReturn(Arrays.asList(keys));
    }

    protected void setAdminKeysets(Map<Integer, AdminKeyset> adminKeysets) {
        when(adminKeysetSnapshot.getAllKeysets()).thenReturn(adminKeysets);
    }

    protected void setKeysetKeys(int maxKeyId, KeysetKey... keys) throws Exception {
        JsonObject metadata = new JsonObject();
        metadata.put("max_key_id", maxKeyId);
        List<KeysetKey> keysetKeys = Arrays.asList(keys);
        HashMap<Integer, KeysetKey> keyMap = new HashMap<>();
        keysetKeys.forEach(i -> keyMap.put(i.getId(), i));
        when(keysetKeyProvider.getMetadata()).thenReturn(metadata);
        when(keysetKeyProviderSnapshot.getAllKeysetKeys()).thenReturn(keysetKeys);
        when(keysetKeyProviderSnapshot.getKey(anyInt())).thenAnswer(i -> {
            return keyMap.get(i.getArgument(0));
        });
    }

    protected void setKeypairs(List<ClientSideKeypair> keypairs) throws Exception {
        JsonObject metadata = new JsonObject();
        HashMap<String, ClientSideKeypair> keypairMap = new HashMap<>();
        HashMap<Integer, List<ClientSideKeypair>> siteKeypairMap = new HashMap<>();
        keypairs.forEach(k -> {
            keypairMap.put(k.getSubscriptionId(), k);
            siteKeypairMap.computeIfAbsent(k.getSiteId(), id -> new ArrayList<>()).add(k);
        });
        when(keypairProvider.getMetadata()).thenReturn(metadata);
        when(keypairProvider.getAll()).thenReturn(keypairMap.values());
        ClientSideKeypairStoreSnapshot snapshot = new ClientSideKeypairStoreSnapshot(keypairMap, siteKeypairMap);
        when(keypairProvider.getSnapshot()).thenReturn(snapshot);
    }

    protected void setEncryptionKeyAcls(Map<Integer, EncryptionKeyAcl> acls) {
        when(keyAclProviderSnapshot.getAllAcls()).thenReturn(acls);
    }

    protected void setOperatorKeys(OperatorKey... operatorKeys) {
        when(operatorKeyProvider.getAll()).thenReturn(Arrays.asList(operatorKeys));
    }

    protected void setAdminUsers(AdminUser... adminUsers) {
        when(adminUserProvider.getAll()).thenReturn(Arrays.asList(adminUsers));
    }

    protected static Handler<AsyncResult<HttpResponse<Buffer>>> expectHttpError(VertxTestContext testContext, int errorCode) {
        return ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(errorCode, response.statusCode());
            testContext.completeNow();
        };
    }

    protected EncryptionKeyAcl makeKeyAcl(boolean isWhitelist, Integer... siteIds) {
        return new EncryptionKeyAcl(isWhitelist, Arrays.stream(siteIds).collect(Collectors.toSet()));
    }

    protected static class CollectionOfSize implements ArgumentMatcher<Collection> {
        private final int expectedSize;

        public CollectionOfSize(int expectedSize) {
            this.expectedSize = expectedSize;
        }

        public boolean matches(Collection c) {
            return c.size() == expectedSize;
        }
    }

    protected static Collection collectionOfSize(int expectedSize) {
        return argThat(new CollectionOfSize(expectedSize));
    }

    protected static class MapOfSize implements ArgumentMatcher<Map> {
        private final int expectedSize;

        public MapOfSize(int expectedSize) {
            this.expectedSize = expectedSize;
        }

        public boolean matches(Map c) {
            return c.size() == expectedSize;
        }
    }

    protected static Map mapOfSize(int expectedSize) {
        return argThat(new MapOfSize(expectedSize));
    }
}
