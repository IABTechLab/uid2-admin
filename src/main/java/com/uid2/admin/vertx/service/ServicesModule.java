package com.uid2.admin.vertx.service;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import lombok.val;

import java.util.Arrays;

/*
 * This is a temporary module which accepts an array of pre-created singleton services and makes them available as a module.
 * Over time, we would ideally move to letting the DI framework create the services as well - this temporary solution
 * is being used to support a strangler-pattern introduction of DI.
 */
public class ServicesModule  extends AbstractModule {
    private final IService[] services;

    public ServicesModule(IService[] services) {
        this.services = services;
    }

    @Override
    protected void configure() {
        Multibinder<IService> interfaceBinder = Multibinder.newSetBinder(binder(), IService.class);
        Arrays.stream(services).forEach(s -> {
            bind((Class<IService>)s.getClass()).toInstance(s);
            interfaceBinder.addBinding().toInstance(s);
        });
    }
}
