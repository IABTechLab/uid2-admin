package com.uid2.admin.vertx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.vertx.service.EnclaveIdService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.EnclaveIdentifier;
import com.uid2.shared.util.Mapper;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import org.instancio.Instancio;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnclaveIdServiceTest extends ServiceTestBase {
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();

    @Override
    protected IService createService() {
        return new EnclaveIdService(auth, writeLock, enclaveStoreWriter, enclaveIdentifierProvider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"aws-nitro", "azure-cc", "gcp-oidc"})
    public void enclaveIdAdd(String protocol, Vertx vertx, VertxTestContext vertxTestContext) {
        fakeAuth(Role.PRIVILEGED);

        EnclaveIdentifier expectedEnclaveId = Instancio.of(EnclaveIdentifier.class)
                .set(field(EnclaveIdentifier::getProtocol), protocol)
                .create();
        post(vertx, vertxTestContext, String.format("api/enclave/add?name=%s&protocol=%s&enclave_id=%s", expectedEnclaveId.getName(), expectedEnclaveId.getProtocol(), expectedEnclaveId.getIdentifier()), "", response -> {
            EnclaveIdentifier result = OBJECT_MAPPER.readValue(response.bodyAsString(), EnclaveIdentifier.class);
            assertAll(
                    () -> assertEquals(200, response.statusCode()),
                    () -> expectedEnclaveId.equals(result)
            );
            vertxTestContext.completeNow();
        });
    }
}
