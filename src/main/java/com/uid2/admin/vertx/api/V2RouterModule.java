package com.uid2.admin.vertx.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.ClassPath;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import lombok.val;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class V2RouterModule extends AbstractModule {
    @Provides
    @V2Mapper
    @Singleton
    static ObjectMapper provideMapper() {
        return new ObjectMapper();
    }

    /*
     * Finds all classes in com.uid2.admin.vertx.api which implement IRouterProvider and register them.
     * They are registered both as IRouterProvider and as their individual class.
     */
    @Override
    protected void configure() {
        try {
            Multibinder<IRouteProvider> interfaceBinder = Multibinder.newSetBinder(binder(), IRouteProvider.class);

            val cp = ClassPath.from(getClass().getClassLoader());
            val routerProviders = cp
                    .getTopLevelClasses()
                    .stream()
                    .filter(ci -> ci.getName().startsWith("com.uid2.admin.vertx.api"))
                    .map(ci -> ci.load())
                    .filter(cl -> Arrays.stream(cl.getInterfaces()).anyMatch(interf -> interf == IRouteProvider.class))
                    .map(cl -> (Class<IRouteProvider>)cl)
                    .collect(Collectors.toSet());
            for (val routerProviderClass : routerProviders) {
                bind(routerProviderClass);
                interfaceBinder.addBinding().to(routerProviderClass);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
