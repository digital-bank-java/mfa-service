package com.digitalbank.mfaservice.mfa.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

record CreateEnrollmentRequest(
        @Schema(
                description =
                        "Legacy subject identifier accepted for compatibility and ignored; ownership is derived from the authenticated JWT sub claim.",
                example = "customer-123",
                deprecated = true)
        String subjectId) {}
