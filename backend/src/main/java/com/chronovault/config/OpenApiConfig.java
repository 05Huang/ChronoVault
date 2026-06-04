package com.chronovault.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chronovaultOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ChronoVault API")
                        .description("服务器时间机器 — 智能备份恢复平台 REST API")
                        .version("0.1.0"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new Components()
                        .addSecuritySchemes("Bearer",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    /**
     * Global customizer that adds common error responses (400, 401, 404, 500)
     * to all controller endpoints in the Swagger documentation.
     */
    @Bean
    public OpenApiCustomizer globalErrorResponsesCustomizer() {
        return openApi -> {
            Schema<?> errorSchema = new Schema<>()
                    .type("object")
                    .properties(Map.of(
                            "code", new Schema<Integer>().type("integer").description("错误码"),
                            "message", new Schema<String>().type("string").description("错误消息"),
                            "data", new Schema<>().type("object").nullable(true).description("附加数据"),
                            "timestamp", new Schema<String>().type("string").format("date-time").description("时间戳")
                    ));

            Content errorContent = new Content()
                    .addMediaType("application/json",
                            new MediaType().schema(errorSchema));

            ApiResponse badRequest = new ApiResponse()
                    .description("请求参数错误（参数缺失、类型错误、验证失败）")
                    .content(errorContent);
            ApiResponse unauthorized = new ApiResponse()
                    .description("未认证或 Token 无效/过期")
                    .content(errorContent);
            ApiResponse forbidden = new ApiResponse()
                    .description("无权限访问该资源")
                    .content(errorContent);
            ApiResponse notFound = new ApiResponse()
                    .description("请求的资源不存在")
                    .content(errorContent);
            ApiResponse conflict = new ApiResponse()
                    .description("数据冲突（违反唯一约束等）")
                    .content(errorContent);
            ApiResponse serverError = new ApiResponse()
                    .description("服务器内部错误")
                    .content(errorContent);

            if (openApi.getPaths() != null) {
                openApi.getPaths().forEach((path, pathItem) -> {
                    pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                        ApiResponses responses = operation.getResponses();
                        if (responses == null) {
                            responses = new ApiResponses();
                            operation.setResponses(responses);
                        }
                        // Only add if not already present
                        if (!responses.containsKey("400")) responses.addApiResponse("400", badRequest);
                        if (!responses.containsKey("401")) responses.addApiResponse("401", unauthorized);
                        if (!responses.containsKey("403")) responses.addApiResponse("403", forbidden);
                        if (!responses.containsKey("404")) responses.addApiResponse("404", notFound);
                        if (!responses.containsKey("409")) responses.addApiResponse("409", conflict);
                        if (!responses.containsKey("500")) responses.addApiResponse("500", serverError);
                    });
                });
            }
        };
    }
}
