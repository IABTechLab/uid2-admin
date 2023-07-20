package com.uid2.admin.vertx.service;

import com.uid2.admin.store.writer.ClientSideKeypairStoreWriter;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.store.reader.RotatingClientSideKeypairStore;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientSideKeypairService implements IService {
    private final AuthMiddleware auth;

    private final WriteLock writeLock;
    private final ClientSideKeypairStoreWriter storeWriter;
    private final RotatingClientSideKeypairStore keypairStore;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSideKeypairService.class);

    public ClientSideKeypairService(AuthMiddleware auth,
                                    WriteLock writeLock,
                                    ClientSideKeypairStoreWriter storeWriter,
                                    RotatingClientSideKeypairStore keypairStore) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.keypairStore = keypairStore;
    }

    @Override
    public void setupRoutes(Router router) {
        // TODO endpoints
//        router.get("").handler(
//                auth.handle(this::, Role.ADMINISTRATOR)
//        );
//        router.post("").handler(
//                auth.handle(this::, Role.ADMINISTRATOR)
//        );
//        router.get("").handler(
//                auth.handle(this::, Role.ADMINISTRATOR)
//        );
    }
}
