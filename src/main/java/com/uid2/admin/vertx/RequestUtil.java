package com.uid2.admin.vertx;

import com.google.common.collect.ImmutableSet;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.ClientType;
import com.uid2.shared.model.Site;
import com.uid2.shared.model.SiteUtil;
import com.uid2.shared.store.ISiteStore;
import io.vertx.ext.web.RoutingContext;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class RequestUtil {
    private final static ImmutableSet<String> SupportedProtocols = ImmutableSet.of(
            "trusted",
            "aws-nitro",
            "gcp-oidc",
            "azure-cc"
    );

    public static String getRolesSpec(Set<Role> roles) {
        return String.join(",", roles.stream().map(r -> r.toString()).collect(Collectors.toList()));
    }

    public static Set<Role> getRoles(String rolesSpec) {
        try {
            Set<Role> roles = Arrays.stream(rolesSpec.split(","))
                    .map(r -> r.trim().toUpperCase())
                    .map(r -> Role.valueOf(r))
                    .collect(Collectors.toSet());
            return roles;
        } catch (Exception e) {
            return null;
        }
    }

    public static Set<ClientType> getTypes(String typeSpec) {
        if(typeSpec.isEmpty()) {
            return new HashSet<>();
        }
        try {
            Set<ClientType> types = Arrays.stream(typeSpec.split(","))
                    .map(r -> r.trim().toUpperCase())
                    .map(r -> ClientType.valueOf(r))
                    .collect(Collectors.toSet());
            return types;
        } catch (Exception e) {
            return null;
        }
    }

    public static Site getSiteFromParam(RoutingContext rc, String param, ISiteStore siteProvider) {
        final List<String> siteIds = rc.queryParam(param);
        if (siteIds.isEmpty()) {
            ResponseUtil.error(rc, 400, "must specify site id");
            return null;
        }

        int siteId;
        try {
            siteId = Integer.valueOf(siteIds.get(0));
        } catch (Exception ex) {
            ResponseUtil.error(rc, 400, "unable to parse site id " + ex.getMessage());
            return null;
        }

        return getSite(rc, siteProvider, siteId);
    }

    public static Site getSiteFromUrl(RoutingContext rc, String param, ISiteStore siteProvider) {
        int siteId;
        try {
            siteId = Integer.parseInt(rc.pathParam(param));
        } catch (Exception ex) {
            ResponseUtil.error(rc, 400, "unable to parse site id " + ex.getMessage());
            return null;
        }

        return getSite(rc, siteProvider, siteId);
    }

    private static Site getSite(RoutingContext rc, ISiteStore siteProvider, int siteId) {
        if (!SiteUtil.isValidSiteId(siteId)) {
            ResponseUtil.error(rc, 400, "must specify a valid site id");
            return null;
        }

        final Site site = siteProvider.getSite(siteId);
        if (site == null) {
            ResponseUtil.error(rc, 404, "site not found");
            return null;
        }

        return site;
    }

    public static Optional<Integer> getSiteId(RoutingContext rc, String param) {
        final List<String> siteIds = rc.queryParam(param);
        if (siteIds.isEmpty()) {
            ResponseUtil.error(rc, 400, "must specify site id");
            return Optional.empty();
        }

        int siteId;
        try {
            return Optional.of(Integer.valueOf(siteIds.get(0)));
        } catch (Exception ex) {
            ResponseUtil.error(rc, 400, "unable to parse site id " + ex.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<Integer> getKeysetId(RoutingContext rc, String param) {
        final List<String> siteIds = rc.queryParam(param);
        if (siteIds.isEmpty()) {
            ResponseUtil.error(rc, 400, "must specify keyset id");
            return Optional.empty();
        }

        int siteId;
        try {
            return Optional.of(Integer.valueOf(siteIds.get(0)));
        } catch (Exception ex) {
            ResponseUtil.error(rc, 400, "unable to parse keyset id " + ex.getMessage());
            return Optional.empty();
        }
    }

    public static Boolean getKeyAclType(RoutingContext rc) {
        boolean isWhitelist;
        List<String> types = rc.queryParam("type");
        if (!types.isEmpty()) {
            try {
                String aclType = types.get(0);
                if (aclType.equals("whitelist")) isWhitelist = true;
                else if (aclType.equals("blacklist")) isWhitelist = false;
                else throw new Exception("unsupported type: " + aclType);
            } catch (Exception ex) {
                ResponseUtil.error(rc, 400, "unable to parse ACL type " + ex.getMessage());
                return null;
            }
        } else {
            ResponseUtil.error(rc, 400, "must specify ACL type");
            return null;
        }
        return isWhitelist;
    }

    public static Set<Integer> getIds(List<String> idsSpec) {
        if (idsSpec.isEmpty() || idsSpec.get(0).isEmpty()) return new HashSet<>();
        try {
            Set<Integer> ids = Arrays.stream(idsSpec.get(0).split(","))
                    .map(v -> v.trim().toUpperCase())
                    .map(v -> Integer.valueOf(v))
                    .collect(Collectors.toSet());
            return ids;
        } catch (Exception e) {
            return null;
        }
    }

    public static String validateOperatorProtocol(String protocol) {
        protocol = protocol.toLowerCase();
        if (SupportedProtocols.contains(protocol)) {
            return protocol;
        }
        return null;
    }

    public static Duration getDuration(RoutingContext rc, String paramName) {
        final List<String> values = rc.queryParam(paramName);
        if (!values.isEmpty()) {
            try {
                final long seconds = Long.parseLong(values.get(0));
                if (seconds < 1) throw new Exception("value is not positive");
                return Duration.ofSeconds(seconds);
            } catch (Exception ex) {
                ResponseUtil.error(rc, 400, paramName + " must be positive number of seconds: " + ex.getMessage());
                return null;
            }
        } else {
            ResponseUtil.error(rc, 400, "must specify " + paramName);
            return null;
        }
    }

    public static Duration[] getDurations(RoutingContext rc, String paramName) {
        final List<String> values = rc.queryParam(paramName);
        if (!values.isEmpty()) {
            try {
                final Duration[] durations = Arrays.stream(values.get(0).split(","))
                        .map(v -> Duration.ofSeconds(Long.parseLong(v.trim())))
                        .toArray(Duration[]::new);
                if (Arrays.stream(durations).anyMatch(d -> d.getSeconds() < 1)) throw new Exception("value is not positive");
                return durations;
            } catch (Exception ex) {
                ResponseUtil.error(rc, 400, paramName + " must be comma-separated list of seconds: " + ex.getMessage());
                return null;
            }
        } else {
            ResponseUtil.error(rc, 400, "must specify " + paramName);
            return null;
        }
    }

    public static Optional<String> getString(RoutingContext rc, String paramName) {
        final List<String> values = rc.queryParam(paramName);
        if (values.isEmpty()) return Optional.empty();
        return Optional.of(values.get(0));
    }

    public static Optional<Boolean> getBoolean(RoutingContext rc, String paramName, boolean defaultValue) {
        final List<String> values = rc.queryParam(paramName);
        if (values.isEmpty()) {
            return Optional.of(defaultValue);
        }
        try {
            return Optional.of(Boolean.valueOf(values.get(0)));
        } catch (Exception ex) {
            ResponseUtil.error(rc, 400, "failed to parse " + paramName + ": " + ex.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<Double> getDouble(RoutingContext rc, String paramName) {
        final List<String> values = rc.queryParam(paramName);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.valueOf(values.get(0)));
        } catch (Exception ex) {
            ResponseUtil.error(rc, 400, "failed to parse " + paramName + ": " + ex.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<LocalDate> getDate(RoutingContext rc, String paramName, DateTimeFormatter formatter) {
        final List<String> values = rc.queryParam(paramName);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(values.get(0), formatter));
        } catch (Exception ex) {
            ResponseUtil.error(rc, 400, "failed to parse " + paramName + ": " + ex.getMessage());
            return Optional.empty();
        }
    }
}
