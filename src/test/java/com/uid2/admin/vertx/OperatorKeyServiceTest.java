package com.uid2.admin.vertx;

import com.uid2.admin.model.Site;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

public class OperatorKeyServiceTest extends ServiceTestBase {
    @Override
    protected IService createService() {
        return new OperatorKeyService(config, auth, writeLock, storageManager, operatorKeyProvider, siteProvider, keyGenerator);
    }

    private void checkOperatorKeyResponse(OperatorKey[] expectedOperators, Object[] actualOperators) {
        assertEquals(expectedOperators.length, actualOperators.length);
        for (int i = 0; i < expectedOperators.length; ++i) {
            OperatorKey expectedOperator = expectedOperators[i];
            JsonObject actualOperator = (JsonObject) actualOperators[i];
            assertEquals(expectedOperator.getName(), actualOperator.getString("name"));
            assertEquals(expectedOperator.getContact(), actualOperator.getString("contact"));
            assertEquals(expectedOperator.getProtocol(), actualOperator.getString("protocol"));
            assertEquals(expectedOperator.isDisabled(), actualOperator.getBoolean("disabled"));
            assertEquals(expectedOperator.getSiteId(), actualOperator.getInteger("site_id"));
            assertEquals(expectedOperator.isPublicOperator(), actualOperator.getBoolean("is_public_operator"));
        }
    }

    @Test
    void operatorAdd(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPTOUT);
        roles.add(Role.OPERATOR);
        setSites(new Site(5, "test_site", true));
        OperatorKey[] expectedOperators = {
                new OperatorKey("", "test_operator", "test_operator", "trusted", 0, false, 5, roles, true)
        };

        post(vertx, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=optout&isPublicOperator=true", "", ar -> {
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
    void operatorAddUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        post(vertx, "api/operator/add?name=test_client&protocol=trusted&site_id=5", "", expectHttpError(testContext, 400));
    }

    @Test
    void operatorUpdate(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPERATOR);
        setSites(new Site(5, "test_site", true));
        setOperatorKeys(new OperatorKey("", "test_operator", "test_operator", "trusted", 0, false, null, roles, false));
        OperatorKey[] expectedOperators = {
                new OperatorKey("", "test_operator", "test_operator", "trusted", 0, false, 5, roles, true)
        };

        post(vertx, "api/operator/update?name=test_operator&site_id=5&isPublicOperator=true", "", ar -> {
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
    void operatorUpdateUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        post(vertx, "api/operator/update?name=test_client&site_id=5", "", expectHttpError(testContext, 404));
    }

    //update Public Operator Status to Private
    @Test
    void operatorFlipPublicOperatorStatusViaUpdate(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPERATOR);
        setSites(new Site(5, "test_site", true));
        setOperatorKeys(new OperatorKey("", "test_operator", "test_operator", "trusted", 0, false, null, roles, true));
        OperatorKey[] expectedOperators = {
                new OperatorKey("", "test_operator", "test_operator", "trusted", 0, false, 5, roles, false)
        };

        post(vertx, "api/operator/update?name=test_operator&site_id=5&isPublicOperator=false", "", ar -> {
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
    void operatorFlipPublicOperatorStatusViaUpdatePublicPrivateOperator(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPERATOR);
        setSites(new Site(5, "test_site", true));
        setOperatorKeys(new OperatorKey("", "test_operator", "test_operator", "trusted", 0, false, 5, roles, true));
        OperatorKey[] expectedOperators = {
                new OperatorKey("", "test_operator", "test_operator", "trusted", 0, false, 5, roles, false)
        };

        post(vertx, "api/operator/updatePublicPrivateOperator?name=test_operator&isPublicOperator=false", "", ar -> {
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
}
