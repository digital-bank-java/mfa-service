package com.digitalbank.mfaservice.mfa.application.port;

import com.digitalbank.mfaservice.mfa.domain.TotpCodeVerifier;

public interface TotpProvider extends TotpCodeVerifier {

    String generateSecret();

    String provisioningUri(String secret, String subjectId);
}
