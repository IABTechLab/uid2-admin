//package com.uid2.admin.vertx.service;
//
//import com.uid2.admin.auth.AdminAuthMiddleware;
//import com.uid2.admin.auth.AuditingHandler;
//import com.uid2.admin.legacy.ILegacyClientKeyProvider;
//import com.uid2.admin.legacy.LegacyClientKey;
//import com.uid2.shared.model.ClientType;
//import com.google.common.net.InternetDomainName;
//import com.uid2.admin.store.writer.StoreWriter;
//import com.uid2.admin.vertx.JsonUtil;
//import com.uid2.admin.vertx.RequestUtil;
//import com.uid2.admin.vertx.ResponseUtil;
//import com.uid2.admin.vertx.WriteLock;
//import com.uid2.shared.Const;
//import com.uid2.shared.auth.Role;
//import io.vertx.ext.web.RoutingContext;
//import io.vertx.ext.web.Router;
//
//public class ApplicationRouter {
//
//    private final Router router;
//    private final AuditingHandler auditingHandler;
//    private final AdminAuthMiddleware auth;
//
//    public ApplicationRouter(Vertx vertx, AuditingHandler auditingHandler, AdminAuthMiddleware auth) {
//        this.router = Router.router(vertx);
//        this.auditingHandler = auditingHandler;
//        this.auth =  auth
//    }
//
//    public Router getRouter() {
//        return router;
//    }
//
//    public void get(String path, Handler<RoutingContext> handler, Role... roles) {
//        router.get(path).handler(auth.handle(auditingHandler.handle(handler, roles)));
//    }
//
//    public void post(String path, Handler<RoutingContext> handler, Role... roles) {
//        router.post(path).handler(auth.handle(auditingHandler.handle(handler, roles)));
//    }
//}