package com.uid2.admin.auth;


import com.okta.jwt.IdTokenVerifier;
import com.okta.jwt.JwtVerificationException;
import com.uid2.shared.util.URLConnectionHttpClient;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class TokenRefreshHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenRefreshHandler.class);
    private final IdTokenVerifier idTokenVerifier;
    private final URLConnectionHttpClient httpClient;
    private final String authServer;
    private final Map<String, String> authHeaders;
    public TokenRefreshHandler(IdTokenVerifier idTokenVerifier, JsonObject config) {
        this.idTokenVerifier = idTokenVerifier;
        this.httpClient = new URLConnectionHttpClient(null);
        this.authServer = config.getString("okta_auth_server");

        String base64AuthValue = Base64.getEncoder().encodeToString(String.format("%s:%s", config.getString("okta_client_id"), config.getString("okta_client_secret")).getBytes(StandardCharsets.UTF_8));
        authHeaders = new HashMap<>() {{
            put("Authorization", String.format("Basic %s", base64AuthValue));
        }};
    }
    @Override
    public void handle(RoutingContext rc) {
        String idToken = null;
        String refreshToken = null;
        if (rc.user() != null && rc.user().principal() != null) {
            idToken = rc.user().principal().getString("id_token");
            refreshToken = rc.user().principal().getString("refresh_token");
        }
        if (idToken != null && refreshToken != null) {
            try {
                idTokenVerifier.decode(idToken, null);
            } catch (JwtVerificationException e) {
                refreshToken(rc, refreshToken);
            }
        }
        rc.next();
    }

    private void refreshToken(RoutingContext rc, String refreshToken) {
        HttpResponse<String> response;
        try {
            response = httpClient.post(String.format("%s/v1/token?grant_type=refresh_token&refresh_token=%s", this.authServer, refreshToken), "", this.authHeaders);
        } catch (IOException e) {
            return;
        }
        if(response.statusCode() == 200) {
            JsonObject responseJson = (JsonObject) Json.decodeValue(response.body());
            rc.user().principal().put("access_token", responseJson.getValue("access_token"));
            rc.user().principal().put("id_token", responseJson.getValue("id_token"));
            rc.user().principal().put("refresh_token", responseJson.getValue("refresh_token"));
        }
    }
}
