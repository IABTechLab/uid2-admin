package com.uid2.admin.audit;

import java.util.List;

@FunctionalInterface
public interface AuditHandler<E> {
    List<OperationModel> handle(E var);
}
