package com.uid2.admin.vertx.test;

@FunctionalInterface
public interface ExceptionHandler<T> {
    void handle(T t) throws Exception;
}
