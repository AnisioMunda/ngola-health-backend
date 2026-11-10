package com.ngolahealth.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Documentação OpenAPI. Swagger UI em /api/swagger-ui.html (apenas perfil dev). */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Ngola Health API",
            version = "v1",
            description = "Sistema de gestão hospitalar multi-hospital"),
    security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {}
