package com.uid2.admin.vertx;

import ch.qos.logback.core.joran.conditional.IfAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.vertx.service.EnclaveIdService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.EnclaveIdentifier;
import com.uid2.shared.util.Mapper;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EnclaveIdServiceTest extends ServiceTestBase {
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();

    @Override
    protected IService createService() {
        return new EnclaveIdService(auth, writeLock, enclaveStoreWriter, enclaveIdentifierProvider);
    }

    @Test
    public void enclave_getMetadata_Success(Vertx vertx, VertxTestContext vertxTestContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        JsonObject expected = new JsonObject();
        expected.put("test", "testValue");

        when(enclaveIdentifierProvider.getMetadata()).thenReturn(expected);

        get(vertx, vertxTestContext, "api/enclave/metadata", response -> {
            String resultBody = response.bodyAsString();
            assertAll(
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals(expected.toString(), resultBody)
            );
            vertxTestContext.completeNow();
        });
    }

    @Test
    public void enclave_List_Success(Vertx vertx, VertxTestContext vertxTestContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        var expected = Instancio.ofList(EnclaveIdentifier.class).size(10).create();
        when(enclaveIdentifierProvider.getAll()).thenReturn(expected);

        get(vertx, vertxTestContext, "/api/enclave/list", response -> {
            List<EnclaveIdentifier> result = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<List<EnclaveIdentifier>>() {
            });

            assertAll(
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertIterableEquals(expected, result)
            );
            vertxTestContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"aws-nitro", "azure-cc", "gcp-oidc"})
    public void enclaveId_Add_Success(String protocol, Vertx vertx, VertxTestContext vertxTestContext) {
        fakeAuth(Role.PRIVILEGED);

        EnclaveIdentifier enclaveToAdd = Instancio.of(EnclaveIdentifier.class)
                .set(field(EnclaveIdentifier::getProtocol), protocol)
                .create();

        var expected = Instancio.ofList(EnclaveIdentifier.class).size(10).create();
        when(enclaveIdentifierProvider.getAll()).thenReturn(expected);

        post(vertx, vertxTestContext, String.format("api/enclave/add?name=%s&protocol=%s&enclave_id=%s", enclaveToAdd.getName(), enclaveToAdd.getProtocol(), enclaveToAdd.getIdentifier()), "", response -> {
            EnclaveIdentifier result = OBJECT_MAPPER.readValue(response.bodyAsString(), EnclaveIdentifier.class);
            assertAll(
                    () -> assertEquals(200, response.statusCode()),
                    () -> enclaveToAdd.equals(result),
                    () -> verify(enclaveStoreWriter).upload(argThat(enclaves -> enclaves.size() == expected.size() + 1 && enclaves.stream().reduce((a, b) -> b).get().equals(enclaveToAdd)))
            );
            vertxTestContext.completeNow();
        });
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"PRIVILEGED", "SUPER_USER"}, mode = EnumSource.Mode.EXCLUDE)
    public void enclaveId_Add_NotAuthorized(Role role, Vertx vertx, VertxTestContext vertxTestContext) {
        fakeAuth(role);

        EnclaveIdentifier expectedEnclaveId = Instancio.of(EnclaveIdentifier.class)
                .set(field(EnclaveIdentifier::getProtocol), "aws-nitro")
                .create();
        post(vertx, vertxTestContext, String.format("api/enclave/add?name=%s&protocol=%s&enclave_id=%s", expectedEnclaveId.getName(), expectedEnclaveId.getProtocol(), expectedEnclaveId.getIdentifier()), "", response -> {
            assertEquals(401, response.statusCode());
            vertxTestContext.completeNow();
        });
    }

    @ParameterizedTest
    @CsvSource(value = {
            "api/enclave/add?protocol=aws_nitro&enclave_id=123,no name specified",
            "api/enclave/add?name=&protocol=aws_nitro&enclave_id=123,no name specified",
            "api/enclave/add?name=123&protocol=aws-nitro,enclave_id not specified",
            "api/enclave/add?name=123&protocol=aws-nitro&enclave_id=,enclave_id not specified",
            "api/enclave/add?name=123&protocol=&enclave_id=345,no protocol specified",
            "api/enclave/add?name=123&enclave_id=345,no protocol specified"
    })
    public void enclaveId_Add_MissingParameters_400(String url, String expectedError, Vertx vertx, VertxTestContext vertxTestContext) {
        fakeAuth(Role.PRIVILEGED);

        post(vertx, vertxTestContext, url, "", response -> {
            assertAll(
                    () -> assertEquals(400, response.statusCode()),
                    () -> assertEquals(String.format("{\"message\":\"%s\",\"status\":\"error\"}", expectedError), response.bodyAsString())
            );
            vertxTestContext.completeNow();
        });
    }

    @Test
    public void enclaveId_Add_InvalidProtocol_400(Vertx vertx, VertxTestContext vertxTestContext) {
        fakeAuth(Role.PRIVILEGED);

        post(vertx, vertxTestContext, "api/enclave/add?name=456&protocol=invalid&enclave_id=123", "", response -> {
            assertAll(
                    () -> assertEquals(400, response.statusCode()),
                    () -> assertEquals("{\"message\":\"invalid protocol specified\",\"status\":\"error\"}", response.bodyAsString())
            );
            vertxTestContext.completeNow();
        });
    }

    @Test
    public void enclaveId_Add_EnclaveName_Exists_400(Vertx vertx, VertxTestContext vertxTestContext) {
        fakeAuth(Role.PRIVILEGED);

        var expected = Instancio.ofList(EnclaveIdentifier.class).size(10).create();
        var name = expected.get(5).getName();
        when(enclaveIdentifierProvider.getAll()).thenReturn(expected);

        post(vertx, vertxTestContext, String.format("api/enclave/add?name=%s&protocol=aws-nitro&enclave_id=123", name), "", response -> {
            assertAll(
                    () -> assertEquals(400, response.statusCode()),
                    () -> assertEquals("{\"message\":\"enclave name already exists\",\"status\":\"error\"}", response.bodyAsString())
            );
            vertxTestContext.completeNow();
        });
    }

    @Test
    public void enclaveId_Add_Protocol_EnclaveId_Exists_400(Vertx vertx, VertxTestContext vertxTestContext) {
        fakeAuth(Role.PRIVILEGED);

        var expected = Instancio.ofList(EnclaveIdentifier.class).size(10).set(field(EnclaveIdentifier::getProtocol), "aws-nitro").create();
        var identifier = expected.get(5).getIdentifier();
        when(enclaveIdentifierProvider.getAll()).thenReturn(expected);

        post(vertx, vertxTestContext, String.format("api/enclave/add?name=123&protocol=aws-nitro&enclave_id=%s", identifier), "", response -> {
            assertAll(
                    () -> assertEquals(400, response.statusCode()),
                    () -> assertEquals("{\"message\":\"protocol and enclave_id already exist\",\"status\":\"error\"}", response.bodyAsString())
            );
            vertxTestContext.completeNow();
        });
    }

    @Test
    public void enclaveId_Delete_Success(Vertx vertx, VertxTestContext vertxTestContext) {
        fakeAuth(Role.SUPER_USER);

        var expected = Instancio.ofList(EnclaveIdentifier.class).size(10).set(field(EnclaveIdentifier::getProtocol), "aws-nitro").create();
        when(enclaveIdentifierProvider.getAll()).thenReturn(expected);

        var name = expected.get(5).getName();

        post(vertx, vertxTestContext, String.format("api/enclave/del?name=%s", name), "", response -> {
            EnclaveIdentifier result = OBJECT_MAPPER.readValue(response.bodyAsString(), EnclaveIdentifier.class);
            assertAll(
                    () -> assertEquals(200, response.statusCode()),
                    () -> expected.get(5).equals(result),
                    () -> verify(enclaveStoreWriter).upload(argThat(enclaves -> enclaves.size() == expected.size() - 1))
                    );
            vertxTestContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"api/enclave/del?name=", "api/enclave/del"})
    public void enclaveId_Delete_Missing_Name_400(String url, Vertx vertx, VertxTestContext vertxTestContext) {
        fakeAuth(Role.SUPER_USER);

        post(vertx, vertxTestContext, url, "", response -> {
            assertAll(
                    () -> assertEquals(400, response.statusCode()),
                    () -> assertEquals("{\"message\":\"no name specified\",\"status\":\"error\"}", response.bodyAsString())
                    );
            vertxTestContext.completeNow();
        });
    }

    @Test
    public void enclaveId_Delete_Name_NotFound_404(Vertx vertx, VertxTestContext vertxTestContext) {
        fakeAuth(Role.SUPER_USER);

        var expected = Instancio.ofList(EnclaveIdentifier.class).size(10).set(field(EnclaveIdentifier::getProtocol), "aws-nitro").create();
        when(enclaveIdentifierProvider.getAll()).thenReturn(expected);

        post(vertx, vertxTestContext, "api/enclave/del?name=SomeNonexistantName", "", response -> {
            assertAll(
                    () -> assertEquals(404, response.statusCode()),
                    () -> assertEquals("{\"message\":\"enclave id not found\",\"status\":\"error\"}", response.bodyAsString())
                    );
            vertxTestContext.completeNow();
        });
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"SUPER_USER"}, mode = EnumSource.Mode.EXCLUDE)
    public void enclaveId_Delete_NotAuthorized(Role role, Vertx vertx, VertxTestContext vertxTestContext) {
        fakeAuth(role);

        post(vertx, vertxTestContext, String.format("api/enclave/del?name=123"), "", response -> {
            assertEquals(401, response.statusCode());
            vertxTestContext.completeNow();
        });
    }

}
