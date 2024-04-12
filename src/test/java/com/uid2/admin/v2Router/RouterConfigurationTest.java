package com.uid2.admin.v2Router;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.vertx.api.IRouteProvider;
import com.uid2.admin.vertx.api.V2Router;
import com.uid2.admin.vertx.api.cstg.GetClientSideKeypairsBySite;
import com.uid2.admin.vertx.service.ClientSideKeypairService;
import com.uid2.shared.auth.Role;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class RouterConfigurationTest {
    @Mock
    private ClientSideKeypairService clientSideKeypairMock;
    @Mock
    private AdminAuthMiddleware authMiddlewareMock;
    @Mock
    private Router routerMock;
    @Mock
    private Router subrouterMock;
    @Mock
    private Route routeMock;
    @Mock
    private Vertx vertxMock;
    @Mock
    private Handler<RoutingContext> handlerMock;
    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        when(routerMock.route(anyString())).thenReturn(routeMock);
        when(subrouterMock.route(any(), anyString())).thenReturn(routeMock);
        when(authMiddlewareMock.handle(any(), any())).thenReturn(handlerMock);
    }
    @Test
    public void WhenANonBlockingRouteProviderIsUsed_ItIsRegisteredCorrectly() {
        val routeProvider = new GetClientSideKeypairsBySite(clientSideKeypairMock);
        val router = new V2Router(new IRouteProvider[] {routeProvider}, authMiddlewareMock);
        try (MockedStatic<Router> r = mockStatic(Router.class)) {
            r.when(() -> Router.router(vertxMock)).thenReturn(subrouterMock);
            router.setupSubRouter(vertxMock, routerMock);

            verify(routeMock).handler(handlerMock);
            verify(authMiddlewareMock).handle(any(), eq(Role.MAINTAINER), eq(Role.SHARING_PORTAL));
        }
    }
}
