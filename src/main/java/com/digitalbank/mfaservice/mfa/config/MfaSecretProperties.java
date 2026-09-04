package com.digitalbank.mfaservice.mfa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mfa.totp")
public class MfaSecretProperties {

    private String encryptionKey;

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalArgumentException("TOTP secret encryption key must be configured");
        }
        this.encryptionKey = encryptionKey;
    }
}
