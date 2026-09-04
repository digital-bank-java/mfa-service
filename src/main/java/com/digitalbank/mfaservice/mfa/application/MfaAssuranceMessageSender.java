package com.digitalbank.mfaservice.mfa.application;

@FunctionalInterface
public interface MfaAssuranceMessageSender {

    void send(String topic, String key, String payload, MfaAssuranceEventHeaders headers);
}
