package com.uid2.admin.vertx.api.annotations;

import com.uid2.shared.auth.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Roles {
    Role[] value();
}
