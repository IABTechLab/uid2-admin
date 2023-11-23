package com.uid2.admin.v2Router;

import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.uid2.admin.GuiceMockInjectingModule;
import com.uid2.admin.RequireInjectAnnotationsModule;
import com.uid2.admin.vertx.api.cstg.GetClientSideKeypairsBySite;
import com.uid2.admin.vertx.api.V2RouterModule;
import com.uid2.admin.vertx.service.ClientSideKeypairService;
import com.uid2.shared.middleware.AuthMiddleware;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.InvalidClassException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RouterConfigurationTests {
    @Test
    public void IfNeededDependencyIsNotProvided_CreateThrowsAnException() {
        assertThrows(CreationException.class, () -> {
            val injector = Guice.createInjector(new RequireInjectAnnotationsModule(), new V2RouterModule());
            injector.getInstance(GetClientSideKeypairsBySite.class);
        });
    }

    @Test
    public void IfNeededDependenciesAreAvailable_ARouterModuleCanBeCreated() throws InvalidClassException {
        val keypairServiceMock = mock(ClientSideKeypairService.class);
        val authMock = mock(AuthMiddleware.class);

        val injector = Guice.createInjector(
                new RequireInjectAnnotationsModule(),
                new V2RouterModule(),
                new GuiceMockInjectingModule(keypairServiceMock, authMock)
        );
        val siteIdRouter = injector.getInstance(GetClientSideKeypairsBySite.class);
        assertNotNull(siteIdRouter);
    }
}
