package com.uid2.admin.vertx;

import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.OperatorKeyService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.Role;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OperatorKeyServiceTest  extends ServiceTestBase {
    @Override
    protected IService createService() {
        return new OperatorKeyService(config, auth, writeLock, storageManager, operatorKeyProvider, keyGenerator);
    }

    private void checkOperatorKeyResponse(OperatorKey[] expectedOperatorKeys, Object[] actualOperatorKeys) {
        assertEquals(expectedOperatorKeys.length, actualOperatorKeys.length);
        for (int i = 0; i < expectedOperatorKeys.length; ++i) {
            OperatorKey expectedOperatorKey = expectedOperatorKeys[i];
            JsonObject actualOperatorKey = (JsonObject) actualOperatorKeys[i];
            assertEquals(expectedOperatorKey.getName(), actualOperatorKey.getString("name"));
            assertEquals(expectedOperatorKey.getContact(), actualOperatorKey.getString("contact"));
            assertEquals(expectedOperatorKey.isDisabled(), actualOperatorKey.getBoolean("disabled"));
            assertEquals(expectedOperatorKey.getSiteId(), actualOperatorKey.getInteger("site_id"));
            assertEquals(expectedOperatorKey.getProtocol(), actualOperatorKey.getString("protocol"));

            List<Role> actualRoles = actualOperatorKey.getJsonArray("roles").stream()
                    .map(r -> Role.valueOf((String) r))
                    .collect(Collectors.toList());
            assertEquals(expectedOperatorKey.getRoles().size(), actualRoles.size());
            for (Role role : expectedOperatorKey.getRoles()) {
                assertTrue(actualRoles.contains(role));
            }
        }
    }

    @Test
    void operatorKeyAdd(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPTOUT);

        OperatorKey[] expectedOperators = {
                new OperatorKey("", "test_operator", "test_operator", "trusted", 0, false).withRoles(roles)
        };

        post(vertx, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=optout", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkOperatorKeyResponse(expectedOperators, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(storageManager).uploadOperatorKeys(any(), collectionOfSize(1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void operatorKeyAddInvalidRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        post(vertx, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=", "", expectHttpError(testContext, 400));
    }

    @Test
    void operatorKeyUpdateRoles(Vertx vertx, VertxTestContext testContext){
        fakeAuth(Role.OPERATOR_MANAGER);
        setOperatorKeys(new OperatorKey("", "test_operator", "test_operator", "trusted", 0, false));

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPTOUT);
        roles.add(Role.OPERATOR);

        OperatorKey[] expectedOperators = {
                new OperatorKey("", "test_operator", "test_operator", "trusted", 0, false).withRoles(roles)
        };

        post(vertx, "api/operator/roles?name=test_operator&roles=optout,operator", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkOperatorKeyResponse(expectedOperators, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(storageManager).uploadOperatorKeys(any(), collectionOfSize(1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void operatorKeyUpdateInvalidRole(Vertx vertx, VertxTestContext testContext){
        fakeAuth(Role.OPERATOR_MANAGER);
        setOperatorKeys(new OperatorKey("", "test_operator", "test_operator", "trusted", 0, false));
        post(vertx, "api/operator/roles?name=test_operator&roles=", "", expectHttpError(testContext, 400));
    }
}
