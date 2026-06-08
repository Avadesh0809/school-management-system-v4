package com.school.sms.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.Components
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI {
        val scheme = SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
        return OpenAPI()
            .info(Info().title("School Management API").version("1.0.0").description("Production-ready SMS backend"))
            .addSecurityItem(SecurityRequirement().addList("bearer-jwt"))
            .components(Components().addSecuritySchemes("bearer-jwt", scheme))
    }
}
