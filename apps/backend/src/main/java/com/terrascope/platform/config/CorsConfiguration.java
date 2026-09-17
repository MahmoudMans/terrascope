package com.terrascope.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfiguration implements WebMvcConfigurer {

    private final String allowedOrigin;

    public CorsConfiguration(
            @Value(
                    "${terrascope.cors.allowed-origin:"
                            + "http://localhost:3000}"
            )
            String allowedOrigin
    ) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void addCorsMappings(
            CorsRegistry registry
    ) {
        registry
                .addMapping("/api/**")
                .allowedOrigins(
                        allowedOrigin
                )
                .allowedMethods(
                        "GET",
                        "POST",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
