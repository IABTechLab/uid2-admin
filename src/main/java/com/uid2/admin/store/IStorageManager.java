// Copyright (c) 2021 The Trade Desk, Inc
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

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
