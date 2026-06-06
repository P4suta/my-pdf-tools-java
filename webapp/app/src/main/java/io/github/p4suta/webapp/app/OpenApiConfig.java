package io.github.p4suta.webapp.app;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API for the generated OpenAPI document and Swagger UI ({@code /v3/api-docs}, {@code
 * /swagger-ui.html}). The endpoints themselves are documented with {@code @Operation}/{@code @Tag}
 * on {@link JobController}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI pdfbookOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("pdfbook web API")
                                .description(
                                        "Upload a scanned book PDF, watch the conversion progress"
                                                + " over SSE, and download the composed"
                                                + " two-page-spread book.")
                                .version("v1"));
    }
}
