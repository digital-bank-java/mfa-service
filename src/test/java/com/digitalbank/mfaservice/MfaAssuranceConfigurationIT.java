package com.digitalbank.mfaservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.mfaservice.mfa.adapter.kafka.KafkaMfaAssuranceMessageSender;
import com.digitalbank.mfaservice.mfa.application.MfaAssuranceMessageSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = {MfaServiceApplication.class, TestSecurityConfig.class},
        properties = {"mfa.assurance.publisher.enabled=true", "spring.kafka.bootstrap-servers=localhost:19092"})
class MfaAssuranceConfigurationIT {

    @Autowired
    private MfaAssuranceMessageSender messageSender;

    @Test
    void enabledAssurancePublishingWiresKafkaMessageSender() {
        assertThat(messageSender).isInstanceOf(KafkaMfaAssuranceMessageSender.class);
    }
}
