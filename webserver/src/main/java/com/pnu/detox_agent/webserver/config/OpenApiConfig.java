package com.pnu.detox_agent.webserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI analyticsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DNS Analytics Dashboard API")
                .description("DNS query streaming analytics with Redis cache and PostgreSQL persistence")
                .version("v1")
                .contact(new Contact().name("Detox Agent Backend")));
    }
}
