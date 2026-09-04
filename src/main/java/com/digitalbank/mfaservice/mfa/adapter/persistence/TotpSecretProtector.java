package com.digitalbank.mfaservice.mfa.adapter.persistence;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class TotpSecretProtector {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom;

    public TotpSecretProtector(String base64Key) {
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("TOTP secret encryption key must be base64 encoded", exception);
        }
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("TOTP secret encryption key must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }

    public String encrypt(String secret) {
        try {
            var iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            var cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            var ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            var payload = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt TOTP secret", exception);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            var payload = Base64.getDecoder().decode(ciphertext);
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Encrypted TOTP secret payload is too short");
            }
            var iv = Arrays.copyOfRange(payload, 0, IV_LENGTH_BYTES);
            var encryptedSecret = Arrays.copyOfRange(payload, IV_LENGTH_BYTES, payload.length);
            var cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encryptedSecret), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt stored TOTP secret", exception);
        }
    }
}
