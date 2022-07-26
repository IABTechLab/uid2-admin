package com.uid2.admin.vertx;

import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import com.uid2.admin.Constants;
import com.uid2.admin.audit.*;
import com.uid2.admin.vertx.service.AdminKeyService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import software.amazon.awssdk.services.qldbsession.QldbSessionClient;
import software.amazon.qldb.QldbDriver;
import software.amazon.qldb.Result;
import software.amazon.qldb.RetryPolicy;

import java.time.Instant;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * These tests do not establish the validity of the AdminKeyService itself; rather, this test ensures that the logs to
 * AWS' QLDB are correct.
 */

public class AdminLogTest extends ServiceTestBase {

    private QldbDriver qldbDriver = QldbDriver.builder()
            .ledger(Constants.QLDB_LEDGER_NAME)
            .transactionRetryPolicy(RetryPolicy.builder().maxRetries(3).build())
            .sessionClientBuilder(QldbSessionClient.builder())
            .build();

    private IonSystem ionSys = IonSystemBuilder.standard().build();

    private final String realAdmin = "uid2-secret-rotation";
    private final String fakeAdmin = "some-fake-admin";

    @Captor
    private ArgumentCaptor<AuditModel> auditModelCaptor;

    @Override
    protected IService createService() {
        return new AdminKeyService(config, audit, auth, writeLock, storageManager, adminUserProvider, keyGenerator);
    }

    /**
     * Verifies the connection to the QLDB and checks the existence of the table AuditMiddleware writes to
     */
    private void checkLogServer(){
        if(!qldbConnection){
            return;
        }
        qldbDriver.execute(txn -> {
            Result r = txn.execute("SELECT * FROM information_schema.user_tables AS i WHERE i.name = ?",
                    ionSys.newString(Constants.QLDB_TABLE_NAME));
            assertTrue(r.iterator().hasNext());
        });
    }

    private boolean hasNullUser(Iterator<IonValue> iterator){
        boolean match = false;
        while(iterator.hasNext()){
            IonStruct output = (IonStruct) iterator.next();
            if(output.get("adminUser").isNullValue()){
                match = true;
            }
            if(match){
                break;
            }
        }
        return match;
    }

    private void successResponse(AsyncResult<HttpResponse<Buffer>> ar){
        assertTrue(ar.succeeded());
        HttpResponse<Buffer> response = ar.result();
        assertEquals(200, response.statusCode());
    }


    @BeforeEach
    void setupAdminProvider() throws Exception {
        setAdminLoad(1);
        JsonObject jo = new JsonObject().put("version", 256).put("generated", 1645574695)
                .put("admins", new JsonObject().put("location", "uid2/admins/admins.json"));
        setAdminMetaData(jo);
    }

    @Test
    void noLogOnFailedAuth(Vertx vertx, VertxTestContext testContext) throws InterruptedException{
        Thread.sleep(2000);
        checkLogServer();
        long currentEpochSecond = Instant.now().getEpochSecond();
        fakeAuth();
        get(vertx, "api/admin/metadata", expectHttpError(testContext, 401));
        get(vertx, "api/admin/list", expectHttpError(testContext, 401));
        if(qldbConnection) {
            qldbDriver.execute(txn -> {
                Result r = txn.execute("SELECT * FROM " + Constants.QLDB_TABLE_NAME +
                        " AS t WHERE t.timeEpochSecond >= ?", ionSys.newInt((int) currentEpochSecond));
                assertFalse(r.iterator().hasNext());
            });
        }
        else{
            Mockito.verify(mockedAuditWriter, never()).writeLog(any());
        }
    }

    @Test
    void noLogOnGetMetadata(Vertx vertx, VertxTestContext testContext) throws InterruptedException {
        Thread.sleep(2000);
        checkLogServer();
        long currentEpochSecond = Instant.now().getEpochSecond();
        fakeAuth(Role.ADMINISTRATOR);
        get(vertx, "api/admin/metadata", ar -> {
            successResponse(ar);
            if(qldbConnection) {
                qldbDriver.execute(txn -> {
                    Result r = txn.execute("SELECT * FROM " + Constants.QLDB_TABLE_NAME +
                                    " AS t WHERE t.timeEpochSecond >= ? AND t.itemActioned = ? AND t.actionTaken = 'LIST'",
                            ionSys.newInt((int) currentEpochSecond),
                            ionSys.newString(Constants.DEFAULT_ITEM_KEY));
                    assertFalse(r.iterator().hasNext());
                });
            }
            else{
                Mockito.verify(mockedAuditWriter, never()).writeLog(any());
            }
            testContext.completeNow();
        });
    }

    @Test
    void logOnListAdmin(Vertx vertx, VertxTestContext testContext) throws InterruptedException{
        Thread.sleep(2000);
        checkLogServer();
        long currentEpochSecond = Instant.now().getEpochSecond();
        fakeAuth(Role.ADMINISTRATOR);
        get(vertx, "api/admin/list", ar -> {
            successResponse(ar);
            if(qldbConnection) {
                qldbDriver.execute(txn -> {
                    Result r = txn.execute("SELECT * FROM " + Constants.QLDB_TABLE_NAME +
                                    " AS t WHERE t.timeEpochSecond >= ? AND t.itemActioned = ? AND t.actionTaken = 'LIST'",
                            ionSys.newInt((int) currentEpochSecond),
                            ionSys.newString(Constants.DEFAULT_ITEM_KEY));
                    assertTrue(hasNullUser(r.iterator()));
                });
            }
            else{
                ExpectedAuditModel expected = new ExpectedAuditModel(Type.ADMIN, Constants.DEFAULT_ITEM_KEY, Actions.LIST, null, null);
                Mockito.verify(mockedAuditWriter, atLeastOnce()).writeLog(auditModelCaptor.capture());
                assertTrue(expected.matches((QLDBAuditModel) auditModelCaptor.getValue()));
            }
            testContext.completeNow();
        });
    }

    @Test
    void logOnAdminReveal(Vertx vertx, VertxTestContext testContext) throws InterruptedException{
        Thread.sleep(2000);
        checkLogServer();
        long currentEpochSecond = Instant.now().getEpochSecond();
        fakeAuth(Role.ADMINISTRATOR);
        get(vertx, "api/admin/reveal?name=" + realAdmin, ar -> { //need query parameters
            successResponse(ar);
            if(qldbConnection) {
                qldbDriver.execute(txn -> {
                    Result r = txn.execute("SELECT * FROM " + Constants.QLDB_TABLE_NAME +
                                    " AS t WHERE t.timeEpochSecond >= ? AND t.itemActioned = ? AND t.actionTaken = 'GET'",
                            ionSys.newInt((int) currentEpochSecond),
                            ionSys.newString(realAdmin));
                    assertTrue(hasNullUser(r.iterator()));
                });
            }
            else{
                ExpectedAuditModel expected = new ExpectedAuditModel(Type.ADMIN, realAdmin, Actions.GET, null, null);
                Mockito.verify(mockedAuditWriter, atLeastOnce()).writeLog(auditModelCaptor.capture());
                assertTrue(expected.matches((QLDBAuditModel) auditModelCaptor.getValue()));
            }
            testContext.completeNow();
        });
    }

    @Test
    void logOnAdminAdd(Vertx vertx, VertxTestContext testContext) throws InterruptedException{
        Thread.sleep(2000);
        checkLogServer();
        long currentEpochSecond = Instant.now().getEpochSecond();
        fakeAuth(Role.ADMINISTRATOR);
        post(vertx, "api/admin/add?name=" + fakeAdmin + "&roles=ADMINISTRATOR", "", ar -> {
            successResponse(ar);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if(qldbConnection) {
                qldbDriver.execute(txn -> {
                    Result r = txn.execute("SELECT * FROM " + Constants.QLDB_TABLE_NAME +
                                    " AS t WHERE t.timeEpochSecond >= ? AND t.itemActioned = ? AND t.actionTaken = 'CREATE'",
                            ionSys.newInt((int) currentEpochSecond),
                            ionSys.newString(fakeAdmin));
                    assertTrue(hasNullUser(r.iterator()));
                });
            }
            else{
                ExpectedAuditModel expected = new ExpectedAuditModel(Type.ADMIN, fakeAdmin, Actions.CREATE, null, null);
                Mockito.verify(mockedAuditWriter, atLeastOnce()).writeLog(auditModelCaptor.capture());
                assertTrue(expected.matches((QLDBAuditModel) auditModelCaptor.getValue()));
            }
            testContext.completeNow();
        });
    }

    @Test
    void logOnAdminDel(Vertx vertx, VertxTestContext testContext) throws InterruptedException{
        Thread.sleep(2000);
        checkLogServer();
        long currentEpochSecond = Instant.now().getEpochSecond();
        fakeAuth(Role.ADMINISTRATOR);
        post(vertx, "api/admin/del?name=" + realAdmin, "", ar -> {
           successResponse(ar);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if(qldbConnection) {
                qldbDriver.execute(txn -> {
                    Result r = txn.execute("SELECT * FROM " + Constants.QLDB_TABLE_NAME +
                                    " AS t WHERE t.timeEpochSecond >= ? AND t.itemActioned = ? AND t.actionTaken = 'DELETE'",
                            ionSys.newInt((int) currentEpochSecond),
                            ionSys.newString(realAdmin));
                    assertTrue(hasNullUser(r.iterator()));
                });
            }
            else{
                ExpectedAuditModel expected = new ExpectedAuditModel(Type.ADMIN, realAdmin, Actions.DELETE, null, null);
                Mockito.verify(mockedAuditWriter, atLeastOnce()).writeLog(auditModelCaptor.capture());
                assertTrue(expected.matches((QLDBAuditModel) auditModelCaptor.getValue()));
            }
            testContext.completeNow();
        });
    }

    @Test
    void logOnAdminDisableEnable(Vertx vertx, VertxTestContext testContext) throws InterruptedException{
        Thread.sleep(2000);
        checkLogServer();
        long currentEpochSecond = Instant.now().getEpochSecond();
        fakeAuth(Role.ADMINISTRATOR);
        post(vertx, "api/admin/disable?name=" + realAdmin, "", ar -> {
            successResponse(ar);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if(qldbConnection) {
                qldbDriver.execute(txn -> {
                    Result r = txn.execute("SELECT * FROM " + Constants.QLDB_TABLE_NAME +
                                    " AS t WHERE t.timeEpochSecond >= ? AND t.itemActioned = ? AND t.actionTaken = 'DISABLE'",
                            ionSys.newInt((int) currentEpochSecond),
                            ionSys.newString(realAdmin));
                    assertTrue(hasNullUser(r.iterator()));
                });
            }
            else{
                ExpectedAuditModel expected = new ExpectedAuditModel(Type.ADMIN, realAdmin, Actions.DISABLE, null, null);
                Mockito.verify(mockedAuditWriter, atLeastOnce()).writeLog(auditModelCaptor.capture());
                assertTrue(expected.matches((QLDBAuditModel) auditModelCaptor.getValue()));
            }
        });
        Thread.sleep(2000);
        long nextCurrentEpochSecond = Instant.now().getEpochSecond();
        post(vertx, "api/admin/enable?name=" + realAdmin, "", ar -> {
            successResponse(ar);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if(qldbConnection) {
                qldbDriver.execute(txn -> {
                    Result r = txn.execute("SELECT * FROM " + Constants.QLDB_TABLE_NAME +
                                    " AS t WHERE t.timeEpochSecond >= ? AND t.itemActioned = ? AND t.actionTaken = 'ENABLE'",
                            ionSys.newInt((int) nextCurrentEpochSecond),
                            ionSys.newString(realAdmin));
                    assertTrue(hasNullUser(r.iterator()));
                });
            }
            else{
                ExpectedAuditModel expected = new ExpectedAuditModel(Type.ADMIN, realAdmin, Actions.ENABLE, null, null);
                Mockito.verify(mockedAuditWriter, atLeastOnce()).writeLog(auditModelCaptor.capture());
                assertTrue(expected.matches((QLDBAuditModel) auditModelCaptor.getValue()));
            }
            testContext.completeNow();
        });
    }

    @Test
    void logOnAdminRekey(Vertx vertx, VertxTestContext testContext) throws InterruptedException{
        Thread.sleep(2000);
        checkLogServer();
        long currentEpochSecond = Instant.now().getEpochSecond();
        fakeAuth(Role.ADMINISTRATOR);
        post(vertx, "api/admin/rekey?name=" + realAdmin, "", ar -> {
            successResponse(ar);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if(qldbConnection) {
                qldbDriver.execute(txn -> {
                    Result r = txn.execute("SELECT * FROM " + Constants.QLDB_TABLE_NAME +
                                    " AS t WHERE t.timeEpochSecond >= ? " +
                                    "AND t.itemActioned = ? AND t.actionTaken = 'UPDATE'",
                            ionSys.newInt((int) currentEpochSecond),
                            ionSys.newString(realAdmin));
                    assertTrue(hasNullUser(r.iterator()));
                });
            }
            else{
                ExpectedAuditModel expected = new ExpectedAuditModel(Type.ADMIN, realAdmin, Actions.UPDATE, null, null);
                Mockito.verify(mockedAuditWriter, atLeastOnce()).writeLog(auditModelCaptor.capture());
                assertTrue(expected.matches((QLDBAuditModel) auditModelCaptor.getValue()));
            }
            testContext.completeNow();
        });
    }

    @Test
    void logOnAdminSetRoles(Vertx vertx, VertxTestContext testContext) throws InterruptedException{
        Thread.sleep(2000);
        checkLogServer();
        long currentEpochSecond = Instant.now().getEpochSecond();
        fakeAuth(Role.ADMINISTRATOR);
        post(vertx, "api/admin/roles?name=" + realAdmin + "&roles=ADMINISTRATOR", "", ar -> {
            successResponse(ar);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if(qldbConnection) {
                qldbDriver.execute(txn -> {
                    Result r = txn.execute("SELECT * FROM " + Constants.QLDB_TABLE_NAME +
                                    " AS t WHERE t.timeEpochSecond >= ? " +
                                    "AND t.itemActioned = ? AND t.actionTaken = 'UPDATE'",
                            ionSys.newInt((int) currentEpochSecond),
                            ionSys.newString(realAdmin));
                    assertTrue(hasNullUser(r.iterator()));
                });
            }
            else{
                ExpectedAuditModel expected = new ExpectedAuditModel(Type.ADMIN, realAdmin, Actions.UPDATE, null, null);
                Mockito.verify(mockedAuditWriter, atLeastOnce()).writeLog(auditModelCaptor.capture());
                assertTrue(expected.matches((QLDBAuditModel) auditModelCaptor.getValue()));
            }
            testContext.completeNow();
        });
    }

    private static class ExpectedAuditModel{
        public final Type itemType;
        public final String itemKey;
        public final Actions actionTaken;
        public final String itemHash;
        public final String summary;

        public ExpectedAuditModel(Type itemType, String itemKey, Actions actionTaken,
                                  String itemHash, String summary){
            this.itemType = itemType;
            this.itemKey = itemKey;
            this.actionTaken = actionTaken;
            this.itemHash = itemHash;
            this.summary = summary;
        }

        public boolean matches(QLDBAuditModel model){
            if(itemType != null && !itemType.equals(model.itemType)) return false;
            if(itemKey != null && !itemKey.equals(model.itemKey)) return false;
            if(actionTaken != null && !actionTaken.equals(model.actionTaken)) return false;
            if(itemHash != null && !itemHash.equals(model.itemHash)) return false;
            if(summary != null && !summary.equals(model.summary)) return false;
            return true;
        }
    }
}
