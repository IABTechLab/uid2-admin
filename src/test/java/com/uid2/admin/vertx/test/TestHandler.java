package com.uid2.admin.vertx.test;

@FunctionalInterface
public interface TestHandler<T> {
    void handle(T t) throws Exception;
}
