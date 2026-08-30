package com.digitalbank.mfaservice;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@OpenAPIDefinition(
        info =
                @Info(
                        title = "Digital Bank Multi-Factor Authentication Service API",
                        version = "1.0.0",
                        description = "Internal API foundation for future multi-factor authentication workflows."))
@SpringBootApplication
@ConfigurationPropertiesScan
public class MfaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MfaServiceApplication.class, args);
    }
}
