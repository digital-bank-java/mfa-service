package com.digitalbank.mfaservice.mfa.application.port;

import java.util.Objects;
import java.util.function.Supplier;

@FunctionalInterface
public interface MfaTransactionRunner {

    <T> T execute(Supplier<T> action);

    static MfaTransactionRunner direct() {
        return new MfaTransactionRunner() {
            @Override
            public <T> T execute(Supplier<T> action) {
                return Objects.requireNonNull(action, "action").get();
            }
        };
    }
}
