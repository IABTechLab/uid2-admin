package com.uid2.admin.v2Router;

import com.uid2.admin.vertx.api.cstg.ClientSideKeypairResponse;
import com.uid2.admin.vertx.api.cstg.GetClientSideKeypairsBySite;
import com.uid2.admin.vertx.service.ClientSideKeypairService;
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

public class SiteId_ClientSideKeypair_Test {
    @Captor
    private ArgumentCaptor<ClientSideKeypairResponse[]> keypairResponseCaptor;
    @Mock
    private ClientSideKeypairService clientSideKeypairMock;
    @Mock
    private RoutingContext contextMock;
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

        val service = new GetClientSideKeypairsBySite(clientSideKeypairMock);
        service.handleGetClientSideKeys(contextMock, siteIdToTest);

        verify(contextMock).json(keypairResponseCaptor.capture());
        val response = keypairResponseCaptor.getValue();
        assertEquals(1, response.length);
        val item = response[0];
        assertEquals(siteIdToTest, item.siteId);
        assertEquals(fakePublicKey, item.publicKey);
    }
}
