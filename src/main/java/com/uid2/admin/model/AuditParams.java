package com.uid2.admin.model;

import java.util.Collections;
import java.util.List;

public class AuditParams {
    private final List<String> queryParams;
    private final List<String> bodyParams;

    public AuditParams(List<String> queryParams, List<String> bodyParams) {
        this.queryParams = queryParams != null
                ? Collections.unmodifiableList(queryParams)
                : Collections.emptyList();
        this.bodyParams = bodyParams != null
                ? Collections.unmodifiableList(bodyParams)
                : Collections.emptyList();
    }

    public List<String> getQueryParams() {
        return queryParams;
    }

    public List<String> getBodyParams() {
        return bodyParams;
    }
}
