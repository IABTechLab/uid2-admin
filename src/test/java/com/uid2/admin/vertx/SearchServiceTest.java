package com.uid2.admin.vertx;

import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SearchService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SearchServiceTest extends ServiceTestBase {
    private final static String searchUrl = "api/search/keyOrSecret";
    @Override
    protected IService createService() {
        return new SearchService(auth);
    }

    @ParameterizedTest
    @MethodSource("nonAdminRolesTestData")
    void searchAsNonAdminFails(Role role, Vertx vertx, VertxTestContext testContext){
        fakeAuth(role);
        get(vertx, searchUrl, expectHttpError(testContext, 401));
    }

    @Test
    void searchAsAdminPasses(Vertx vertx, VertxTestContext testContext){
        fakeAuth(Role.ADMINISTRATOR);
        get(vertx, searchUrl, response -> {
            assertTrue(response.succeeded());
            HttpResponse httpResponse = response.result();
            assertEquals(200, httpResponse.statusCode());
            testContext.completeNow();
        });
    }

    @Test
    void searchWithoutRoleFails(Vertx vertx, VertxTestContext testContext){
        get(vertx, searchUrl, expectHttpError(testContext, 401));
    }

    static Stream<Arguments> nonAdminRolesTestData(){
        return Stream.of(
                Arguments.of(Role.GENERATOR),
                Arguments.of(Role.MAPPER),
                Arguments.of(Role.ID_READER),
                Arguments.of(Role.SHARER),
                Arguments.of(Role.OPERATOR),
                Arguments.of(Role.OPTOUT),
                Arguments.of(Role.CLIENTKEY_ISSUER),
                Arguments.of(Role.OPERATOR_MANAGER),
                Arguments.of(Role.SECRET_MANAGER),
                Arguments.of(Role.SHARING_PORTAL),
                Arguments.of(Role.PRIVATE_SITE_REFRESHER)
        );
    }
}
