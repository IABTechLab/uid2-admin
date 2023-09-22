package com.uid2.admin;

import com.google.inject.AbstractModule;

/*
 * This is used as part of the gradual introduction of DI within the codebase to ensure Google Guice doesn't try
 * to instantiate anything that hasn't been marked as available for automated construction.
 * Eventually we probably won't want this, but this helps ensure a staged DI adoption doesn't do anything unexpected.
 */
public class RequireInjectAnnotationsModule extends AbstractModule {
    @Override
    protected void configure() {
        // Prevent Guice from using any constructors which haven't been marked with the @Inject attribute
        binder().requireAtInjectOnConstructors();
    }
}
