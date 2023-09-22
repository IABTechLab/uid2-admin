package com.uid2.admin.v2Router;

import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.uid2.admin.GuiceMockInjectingModule;
import com.uid2.admin.RequireInjectAnnotationsModule;
import com.uid2.admin.vertx.api.SiteIdRouter;
import com.uid2.admin.vertx.api.V2RouterModule;
import com.uid2.admin.vertx.service.ClientSideKeypairService;
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
            injector.getInstance(SiteIdRouter.class);
        });
    }

    @Test
    public void IfNeededDependenciesAreAvailable_ARouterModuleCanBeCreated() throws InvalidClassException {
        val keypairServiceMock = mock(ClientSideKeypairService.class);

        val injector = Guice.createInjector(
                new RequireInjectAnnotationsModule(),
                new V2RouterModule(),
                new GuiceMockInjectingModule(keypairServiceMock)
        );
        val siteIdRouter = injector.getInstance(SiteIdRouter.class);
        assertEquals("/sites/:siteId/", siteIdRouter.getBasePath());
    }
}
