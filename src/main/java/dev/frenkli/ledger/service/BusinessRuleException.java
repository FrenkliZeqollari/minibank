package dev.frenkli.ledger.service;

public class BusinessRuleException extends RuntimeException {

    private final BusinessRule rule;

    public BusinessRuleException(BusinessRule rule, String detail) {
        super(detail);
        this.rule = rule;
    }

    public BusinessRule getRule() {
        return rule;
    }
}
