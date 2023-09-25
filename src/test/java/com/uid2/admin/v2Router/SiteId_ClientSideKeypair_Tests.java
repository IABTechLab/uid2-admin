package com.uid2.admin.v2Router;

import com.google.inject.Guice;
import com.uid2.admin.GuiceMockInjectingModule;
import com.uid2.admin.RequireInjectAnnotationsModule;
import com.uid2.admin.vertx.api.ClientSideKeypairResponse;
import com.uid2.admin.vertx.api.SiteIdRouter;
import com.uid2.admin.vertx.api.V2RouterModule;
import com.uid2.admin.vertx.service.ClientSideKeypairService;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.model.ClientSideKeypair;
import io.vertx.ext.web.RoutingContext;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InvalidClassException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SiteId_ClientSideKeypair_Tests {
    @Captor
    private ArgumentCaptor<ClientSideKeypairResponse[]> keypairResponseCaptor;
    @Mock
    private ClientSideKeypairService clientSideKeypairMock;
    @Mock
    private RoutingContext contextMock;
    @Mock
    private AuthMiddleware authMock;
    ClientSideKeypair createKeypairMock(int siteId, String publicKey) {
        val kpMock = mock(ClientSideKeypair.class);
        when(kpMock.getSiteId()).thenReturn(siteId);
        when(kpMock.encodePublicKeyToString()).thenReturn(publicKey);
        return kpMock;
    }
    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void WhenTheSiteHasASingleKey_ItIsReturned() throws InvalidClassException {
        val siteIdToTest = 5;
        val fakePublicKey = "Fake public key";
        val expectedResult = new ArrayList<ClientSideKeypair>(List.of(
                createKeypairMock(siteIdToTest, fakePublicKey)
        ));

        when(clientSideKeypairMock.getKeypairsBySite(5))
                .thenReturn(expectedResult);

        val injector = Guice.createInjector(
                new RequireInjectAnnotationsModule(),
                new V2RouterModule(),
                new GuiceMockInjectingModule(clientSideKeypairMock, authMock)
        );
        val service = injector.getInstance(SiteIdRouter.class);

        service.handleGetClientSideKeys(contextMock, siteIdToTest);

        verify(contextMock).json(keypairResponseCaptor.capture());
        val response = keypairResponseCaptor.getValue();
        assertEquals(1, response.length);
        val item = response[0];
        assertEquals(siteIdToTest, item.siteId);
        assertEquals(fakePublicKey, item.publicKey);
    }
}
