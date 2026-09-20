package com.navan.expense.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JsonSlf4jAuditPublisher implements AuditPublisher {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit");
    private final JsonMapper jsonMapper;

    public JsonSlf4jAuditPublisher(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void publish(AuditEvent event) {
        AUDIT.info(jsonMapper.writeValueAsString(event));
    }
}
