package com.barberia.api.shared.config;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
public class OpenApiConfiguration {
    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("barberia-api").version("v1")
                .description("Citas, inventario, cobros y ventas presenciales. CARD confirma un cobro externo; no recibe datos bancarios."))
                .components(new Components().addSecuritySchemes("bearerAuth",new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
