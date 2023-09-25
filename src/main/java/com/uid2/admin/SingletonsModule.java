package com.uid2.admin;

import com.google.inject.AbstractModule;
import com.uid2.admin.vertx.service.IService;

import java.util.Arrays;

/*
 This is a temporary module which accepts an array of pre-created singletons and makes them available as a module.
 Over time, we would ideally move to letting the DI framework create the singletons as well - this temporary solution
 is being used to support a strangler-pattern introduction of DI.
*/
public class SingletonsModule extends AbstractModule {
    private final Object[] singletons;

    public SingletonsModule(Object... singletons) {
        this.singletons = singletons;
    }

    @Override
    protected void configure() {
        super.configure();
        Arrays.stream(singletons).forEach(s -> {
            bind((Class<Object>)s.getClass()).toInstance(s);
        });
    }
}
