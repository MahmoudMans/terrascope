package com.terrascope.platform.config;

import com.terrascope.platform.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityWebConfiguration implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public SecurityWebConfiguration(
            AuthInterceptor authInterceptor
    ) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(
            InterceptorRegistry registry
    ) {
        registry
                .addInterceptor(authInterceptor)
                .addPathPatterns("/api/**");
    }
}
