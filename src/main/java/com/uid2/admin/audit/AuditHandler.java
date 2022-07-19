package com.uid2.admin.audit;

@FunctionalInterface
public interface AuditHandler<E> {
    OperationModel handle(E var);
}
