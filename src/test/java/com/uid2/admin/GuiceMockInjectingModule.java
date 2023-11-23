package com.uid2.admin;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.uid2.admin.vertx.service.IService;
import lombok.val;

import java.io.InvalidClassException;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

public class GuiceMockInjectingModule extends AbstractModule {
    private final Object[] mocks;

    public GuiceMockInjectingModule(Object... mocks) throws InvalidClassException {
        for (Object mock : mocks) {
            val mockDetails = mockingDetails(mock);
            if (!mockDetails.isMock()) throw new InvalidClassException(
                    "GuiceMockInjectingModule is for injecting mocks, but found an object which was not a mock:" + mockDetails.getClass().getName()
            );
        }
        this.mocks = mocks;
    }

    @Override
    protected void configure() {
        Arrays.stream(mocks).forEach(mock -> {
            System.out.println("Configuring mock for class " + mock.getClass().getName());
            bind((Class)mock.getClass()).toInstance(mock);
            val interfaces = Arrays.stream(mock.getClass().getInterfaces()).filter(iface -> iface != IService.class);
            interfaces.forEach(iface -> bind((Class)iface).toInstance(mock));
        });
    }
}
