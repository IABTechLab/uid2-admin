package com.uid2.admin.vertx;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Endpoints {
    API_CLIENT_METADATA("/api/client/metadata"),
    API_CLIENT_REWRITE_METADATA("/api/client/rewrite_metadata"),
    API_CLIENT_LIST("/api/client/list"),
    API_CLIENT_LIST_SITEID("/api/client/list/:siteId"),
    API_CLIENT_KEYID("/api/client/keyId"),
    API_CLIENT_CONTACT("/api/client/contact"),
    API_CLIENT_REVEAL("/api/client/reveal"),
    API_CLIENT_ADD("/api/client/add"),
    API_CLIENT_DEL("/api/client/del"),
    API_CLIENT_UPDATE("/api/client/update"),
    API_CLIENT_DISABLE("/api/client/disable"),
    API_CLIENT_ENABLE("/api/client/enable"),
    API_CLIENT_ROLES("/api/client/roles"),
    API_CLIENT_RENAME("/api/client/rename"),

    API_CLIENT_SIDE_KEYPAIRS_ADD("/api/client_side_keypairs/add"),
    API_CLIENT_SIDE_KEYPAIRS_UPDATE("/api/client_side_keypairs/update"),
    API_CLIENT_SIDE_KEYPAIRS_DELETE("/api/client_side_keypairs/delete"),
    API_CLIENT_SIDE_KEYPAIRS_LIST("/api/client_side_keypairs/list"),
    API_CLIENT_SIDE_KEYPAIRS_SUBSCRIPTIONID("/api/client_side_keypairs/:subscriptionId"),
    API_CLIENT_SIDE_KEYPAIRS_BY_SITE("/api/v2/sites/:siteId/client-side-keypairs"),

    API_ENCLAVE_METADATA("/api/enclave/metadata"),
    API_ENCLAVE_LIST("/api/enclave/list"),
    API_ENCLAVE_ADD("/api/enclave/add"),
    API_ENCLAVE_DEL("/api/enclave/del"),

    API_ENCRYPTED_FILES_REFRESH("/api/encrypted-files/refresh"),
    API_ENCRYPTED_FILES_SYNC_NOW("/api/encrypted-files/syncNow"),

    API_KEY_LIST("/api/key/list"),
    API_KEY_LIST_KEYSET_KEYS("/api/key/list_keyset_keys"),
    API_KEY_REWRITE_METADATA("/api/key/rewrite_metadata"),
    API_KEY_ROTATE_MASTER("/api/key/rotate_master"),
    API_KEY_ADD("/api/key/add"),
    API_KEY_ROTATE_SITE("/api/key/rotate_site"),
    API_KEY_ROTATE_KEYSET_KEY("/api/key/rotate_keyset_key"),
    API_KEY_ROTATE_ALL_SITES("/api/key/rotate_all_sites"),

    API_JOB_DISPATCHER_CURRENT_JOB("/api/job-dispatcher/current-job"),
    API_JOB_DISPATCHER_JOB_QUEUE("/api/job-dispatcher/job-queue"),

    API_KEYS_ACL_LIST("/api/keys_acl/list"),
    API_KEYS_ACL_REWRITE_METADATA("/api/keys_acl/rewrite_metadata"),

    API_OPERATOR_METADATA("/api/operator/metadata"),
    API_OPERATOR_LIST("/api/operator/list"),
    API_OPERATOR_REVEAL("/api/operator/reveal"),
    API_OPERATOR_ADD("/api/operator/add"),
    API_OPERATOR_DEL("/api/operator/del"),
    API_OPERATOR_DISABLE("/api/operator/disable"),
    API_OPERATOR_ENABLE("/api/operator/enable"),
    API_OPERATOR_UPDATE("/api/operator/update"),
    API_OPERATOR_ROLES("/api/operator/roles"),

    API_PARTNER_CONFIG_GET("/api/partner_config/get"),
    API_PARTNER_CONFIG_UPDATE("/api/partner_config/update"),

    API_PRIVATE_SITES_REFRESH("/api/private-sites/refresh"),
    API_PRIVATE_SITES_REFRESH_NOW("/api/private-sites/refreshNow"),

    API_SALT_SNAPSHOTS("/api/salt/snapshots"),
    API_SALT_ROTATE("/api/salt/rotate"),
    API_SALT_ROTATE_ZERO("/api/salt/rotate-zero"),

    API_SEARCH("/api/search"),

    API_SERVICE_LINK_LIST("/api/service_link/list"),
    API_SERVICE_LINK_ADD("/api/service_link/add"),
    API_SERVICE_LINK_UPDATE("/api/service_link/update"),
    API_SERVICE_LINK_DELETE("/api/service_link/delete"),

    API_SERVICE_LIST("/api/service/list"),
    API_SERVICE_LIST_SERVICE_ID("/api/service/list/:service_id"),
    API_SERVICE_ADD("/api/service/add"),
    API_SERVICE_UPDATE("/api/service/update"),
    API_SERVICE_DELETE("/api/service/delete"),

    API_SHARING_LISTS("/api/sharing/lists"),
    API_SHARING_LIST_SITEID("/api/sharing/list/:siteId"),
    API_SHARING_KEYSETS("/api/sharing/keysets"),
    API_SHARING_KEYSET("/api/sharing/keyset"),
    API_SHARING_KEYSET_KEYSETID("/api/sharing/keyset/:keyset_id"),
    API_SHARING_KEYSETS_RELATED("/api/sharing/keysets/related"),

    API_SITE_REWRITE_METADATA("/api/site/rewrite_metadata"),
    API_SITE_LIST("/api/site/list"),
    API_SITE_SITEID("/api/site/:siteId"),
    API_SITE_ADD("/api/site/add"),
    API_SITE_ENABLE("/api/site/enable"),
    API_SITE_SET_TYPES("/api/site/set-types"),
    API_SITE_DOMAIN_NAMES("/api/site/domain_names"),
    API_SITE_APP_NAMES("/api/site/app_names"),
    API_SITE_UPDATE("/api/site/update"),

    CLOUD_ENCRYPTION_KEY_METADATA("/api/cloud-encryption-key/metadata"),
    CLOUD_ENCRYPTION_KEY_LIST("/api/cloud-encryption-key/list"),
    CLOUD_ENCRYPTION_KEY_ROTATE("/api/cloud-encryption-key/rotate"),

    LOGIN("/login"),
    LOGOUT("/logout"),
    OPS_HEALTHCHECK("/ops/healthcheck"),
    API_USERINFO("/api/userinfo"),

    WEB_ROOT("/"),
    WEB_JS_MAIN("/js/main.js"),
    WEB_JS_PARTICIPANT_SUMMARY("/js/participantSummary.js"),
    WEB_JS_BOOTSTRAP("/js/bootstrap.bundle.min.js"),
    WEB_CSS_COPY_SVG("/css/copy.svg"),
    WEB_CSS_PARTICIPANT_SUMMARY("/css/participantSummary.css"),
    WEB_CSS_STYLE("/css/style.css"),

    ADM_ONCALL_PARTICIPANT_SUMMARY("/adm/oncall/participant-summary.html"),
    ADM_ONCALL_GENERATE_API_KEY_SECRET("/adm/oncall/generate-api-key-secret.html"),
    ADM_ONCALL_GENERATE_CSTG_KEYPAIR("/adm/oncall/generate-cstg-keypair.html"),
    ADM_ONCALL_SEARCH("/adm/oncall/search.html"),
    ADM_SITE("/adm/site.html"),
    ADM_CLIENT_SIDE_KEYPAIRS("/adm/client-side-keypairs.html"),
    ADM_SERVICES("/adm/services.html"),
    ADM_SERVICE_LINKS("/adm/service-links.html"),
    ADM_CLIENT_KEY("/adm/client-key.html"),
    ADM_KEYSETS("/adm/keysets.html"),
    ADM_ENCRYPTION_KEY("/adm/encryption-key.html"),
    ADM_SALT("/adm/salt.html"),
    ADM_OPERATOR_KEY("/adm/operator-key.html"),
    ADM_ENCLAVE_ID("/adm/enclave-id.html"),
    ADM_ENCLAVE_GCP("/adm/enclave-gcp.html"),
    ADM_ENCLAVE_GCP_V2("/adm/enclave-gcp-v2.html"),
    ADM_PARTNER_CONFIG("/adm/partner-config.html"),
    ADM_KEY_ACL("/adm/key-acl.html");

    private final String path;

    Endpoints(final String path) {
        this.path = path;
    }

    public static Set<String> pathSet() {
        return Stream.of(Endpoints.values()).map(Endpoints::toString).collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return path;
    }
}
