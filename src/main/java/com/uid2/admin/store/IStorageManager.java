package com.uid2.admin.store;

import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.auth.AdminUserProvider;
import com.uid2.admin.model.Site;
import com.uid2.shared.auth.*;
import com.uid2.shared.model.EnclaveIdentifier;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.store.RotatingKeyStore;
import com.uid2.shared.store.RotatingSaltProvider;
import io.vertx.core.json.JsonArray;

import java.util.Collection;
import java.util.Map;

public interface IStorageManager {
    void uploadSites(RotatingSiteStore provider, Collection<Site> sites) throws Exception;
    void uploadClientKeys(RotatingClientKeyProvider provider, Collection<ClientKey> clients) throws Exception;
    void uploadEncryptionKeys(RotatingKeyStore provider, Collection<EncryptionKey> keys, Integer newMaxKeyId) throws Exception;
    void uploadKeyAcls(RotatingKeyAclProvider provider, Map<Integer, EncryptionKeyAcl> acls) throws Exception;
    void uploadSalts(RotatingSaltProvider provider, RotatingSaltProvider.SaltSnapshot newSnapshot) throws Exception;
    void uploadOperatorKeys(RotatingOperatorKeyProvider provider, Collection<OperatorKey> operators) throws Exception;
    void uploadEnclaveIds(EnclaveIdentifierProvider provider, Collection<EnclaveIdentifier> identifiers) throws Exception;
    void uploadAdminUsers(AdminUserProvider provider, Collection<AdminUser> admins) throws Exception;
    void uploadPartners(RotatingPartnerStore partnerConfigProvider, JsonArray partners) throws Exception;
}
