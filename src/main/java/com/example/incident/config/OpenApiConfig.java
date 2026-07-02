package com.example.incident.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI incidentIntakeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Incident Intake Service")
                        .description("Backend intake layer for field incident reports (weather events, "
                                + "security breaches, facility outages). Handles intake, idempotent "
                                + "duplicate submission, lifecycle status transitions, and history.")
                        .version("v0.1.0")
                        .contact(new Contact().name("Manojkumar Sachidanandam")));
    }
}
