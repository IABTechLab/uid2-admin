package com.uid2.admin.vertx.test;

import com.okta.jwt.AccessTokenVerifier;
import com.okta.jwt.IdTokenVerifier;
import com.okta.jwt.Jwt;
import com.uid2.admin.auth.*;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.legacy.LegacyClientKeyStoreWriter;
import com.uid2.admin.legacy.RotatingLegacyClientKeyProvider;
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
import com.uid2.shared.secret.KeyHashResult;
import com.uid2.shared.secret.KeyHasher;
import com.uid2.shared.store.ClientSideKeypairStoreSnapshot;
import com.uid2.shared.store.IKeyStore;
import com.uid2.shared.store.KeysetKeyStoreSnapshot;
import com.uid2.shared.store.reader.*;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(VertxExtension.class)
public abstract class ServiceTestBase {
    protected AutoCloseable mocks;
    protected final JsonObject config = new JsonObject();
    protected final WriteLock writeLock = new WriteLock();

    protected AdminAuthMiddleware auth;
    @Mock private TokenRefreshHandler tokenRefreshHandler;
    @Mock private IdTokenVerifier idTokenVerifier;
    @Mock private AccessTokenVerifier accessTokenVerifier;
    @Mock private Jwt jwt;
    @Mock protected AuthenticationHandler authHandler;
    @Mock protected AuthProvider authProvider;

    @Mock protected StoreWriter storeWriter;
    @Mock protected LegacyClientKeyStoreWriter clientKeyStoreWriter;
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
    @Mock protected RotatingSiteStore siteProvider;
    @Mock protected RotatingLegacyClientKeyProvider clientKeyProvider;
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
        when(authProvider.createAuthHandler(any(), any())).thenReturn(authHandler);
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

        when(authProvider.getAccessTokenVerifier()).thenReturn(accessTokenVerifier);
        when(authProvider.getIdTokenVerifier()).thenReturn(idTokenVerifier);
        doAnswer((invocationOnMock -> {
            User user = User.create(JsonObject.of("id_token", "testIdToken"));
            ((RoutingContext) invocationOnMock.getArgument(0)).setUser(user);
            ((RoutingContext) invocationOnMock.getArgument(0)).next();
            return null;
        })).when(tokenRefreshHandler).handle(any());
        when(idTokenVerifier.decode(anyString(), any())).thenReturn(jwt);
        when(accessTokenVerifier.decode(anyString())).thenReturn(jwt);
        auth = new AdminAuthMiddleware(authProvider, "local");

        IService[] services = {createService()};
        AdminVerticle verticle = new AdminVerticle(config, authProvider, tokenRefreshHandler, services, null);
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

    protected void fakeAuth(Role... roles) { // TODO Update tests for okta role mapping
        when(jwt.getClaims()).thenReturn(Map.of("groups", List.of("developer", "developer-elevated", "infra-admin", "admin"), "environment", "local"));
    }
    protected void fakeAuth(List<String> roles) { // TODO Update tests for okta role mapping
        when(jwt.getClaims()).thenReturn(Map.of("groups", roles, "environment", "local"));
    }


    protected void get(Vertx vertx, VertxTestContext testContext, String endpoint, TestHandler<HttpResponse<Buffer>> handler) {
        WebClient client = WebClient.create(vertx);
        client.getAbs(getUrlForEndpoint(endpoint))
                .send()
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> handler.handle(response))));
    }

    protected void post(Vertx vertx, VertxTestContext testContext, String endpoint, String body, TestHandler<HttpResponse<Buffer>> handler) {
        WebClient client = WebClient.create(vertx);
        client.postAbs(getUrlForEndpoint(endpoint))
                .sendBuffer(body != null ?  Buffer.buffer(body) : null)
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> handler.handle(response))));
    }

    protected void postWithoutBody(Vertx vertx, VertxTestContext testContext, String endpoint, TestHandler<HttpResponse<Buffer>> handler) {
        post(vertx, testContext, endpoint, null, handler);
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

    protected void setClientKeys(LegacyClientKey... clientKeys) {
        when(clientKeyProvider.getAll()).thenReturn(Arrays.asList(clientKeys));
        for (LegacyClientKey clientKey : clientKeys) {
            when(clientKeyProvider.getClientKeyFromHash(clientKey.getKeyHash())).thenReturn(clientKey);
        }
    }

    protected void setClientKeys(Map<String, LegacyClientKey> clientKeys) {
        when(clientKeyProvider.getAll()).thenReturn(clientKeys.values());
        for (Map.Entry<String, LegacyClientKey> entry : clientKeys.entrySet()) {
            String plaintextKey = entry.getKey();
            LegacyClientKey clientKey = entry.getValue();

            when(clientKeyProvider.getClientKey(plaintextKey)).thenReturn(clientKey);
            when(clientKeyProvider.getClientKeyFromHash(clientKey.getKeyHash())).thenReturn(clientKey);
        }
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
        for (OperatorKey operatorKey : operatorKeys) {
            when(operatorKeyProvider.getOperatorKeyFromHash(operatorKey.getKeyHash())).thenReturn(operatorKey);
        }
    }

    protected void setOperatorKeys(Map<String, OperatorKey> operatorKeys) {
        when(operatorKeyProvider.getAll()).thenReturn(operatorKeys.values());
        for (Map.Entry<String, OperatorKey> entry : operatorKeys.entrySet()) {
            String plaintextKey = entry.getKey();
            OperatorKey operatorKey = entry.getValue();

            when(operatorKeyProvider.getOperatorKey(plaintextKey)).thenReturn(operatorKey);
            when(operatorKeyProvider.getOperatorKeyFromHash(operatorKey.getKeyHash())).thenReturn(operatorKey);
        }
    }

    protected static TestHandler<HttpResponse<Buffer>> expectHttpStatus(VertxTestContext testContext, int errorCode) {
        return response -> {
            assertEquals(errorCode, response.statusCode());
            testContext.completeNow();
        };
    }

    protected EncryptionKeyAcl makeKeyAcl(boolean isWhitelist, Integer... siteIds) {
        return new EncryptionKeyAcl(isWhitelist, Arrays.stream(siteIds).collect(Collectors.toSet()));
    }

    protected static class CollectionOfSize<T> implements ArgumentMatcher<Collection<T>> {
        private final int expectedSize;

        public CollectionOfSize(int expectedSize) {
            this.expectedSize = expectedSize;
        }

        public boolean matches(Collection c) {
            return c.size() == expectedSize;
        }
    }

    protected static <T> Collection<T> collectionOfSize(int expectedSize) {
        return argThat(new CollectionOfSize<>(expectedSize));
    }

    protected static class MapOfSize<K, V> implements ArgumentMatcher<Map<K, V>> {
        private final int expectedSize;

        public MapOfSize(int expectedSize) {
            this.expectedSize = expectedSize;
        }

        public boolean matches(Map c) {
            return c.size() == expectedSize;
        }
    }

    protected static <K, V> Map<K, V> mapOfSize(int expectedSize) {
        return argThat(new MapOfSize<>(expectedSize));
    }
}
