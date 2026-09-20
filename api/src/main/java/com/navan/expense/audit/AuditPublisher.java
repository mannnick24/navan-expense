package com.navan.expense.audit;

public interface AuditPublisher {

    void publish(AuditEvent event);
}
