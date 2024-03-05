package com.uid2.admin.vertx.api;

import io.vertx.core.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

public enum EndpointAuthorization {
    SITE_GET("/api/site/list", HttpMethod.GET, new OktaGroup[] {OktaGroup.DEVELOPER}),
    SITE_DELETE("/api/site/del", HttpMethod.POST, new OktaGroup[] {OktaGroup.DEVELOPER_ELEVATED});

    private final String path;
    private final HttpMethod method;
    private final OktaGroup[] groups;

    EndpointAuthorization(final String path, final HttpMethod httpMethod, final OktaGroup[] groups) {
        this.path = path;
        this.method = httpMethod;
        this.groups = groups;
    }

    private static final Map<HttpMethod, Map<String, OktaGroup[]>> methodToEndpointMap = new HashMap<>();
    static {
        for (EndpointAuthorization endpoint : EndpointAuthorization.values()) {
            Map<String, OktaGroup[]> pathToGroupMap = methodToEndpointMap.getOrDefault(endpoint.method, new HashMap<>());
            pathToGroupMap.put(endpoint.path, endpoint.groups);
            methodToEndpointMap.putIfAbsent(endpoint.method, pathToGroupMap);
        }
    }

    public static OktaGroup[] getAllowedGroups(final String path, final HttpMethod httpMethod) {
        return methodToEndpointMap.get(httpMethod).get(path);
    }

    public String getPath() {
        return path;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public OktaGroup[] getGroups() {
        return groups;
    }

    public enum OktaGroup {
        DEVELOPER,
        DEVELOPER_ELEVATED;

        private OktaGroup() {}
    }
}
