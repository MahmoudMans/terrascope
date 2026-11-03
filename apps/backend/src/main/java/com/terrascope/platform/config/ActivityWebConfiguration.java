package com.terrascope.platform.config;

import com.terrascope.platform.activity.ActivityInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ActivityWebConfiguration implements WebMvcConfigurer {

    private final ActivityInterceptor activityInterceptor;

    public ActivityWebConfiguration(
            ActivityInterceptor activityInterceptor
    ) {
        this.activityInterceptor = activityInterceptor;
    }

    @Override
    public void addInterceptors(
            InterceptorRegistry registry
    ) {
        registry
                .addInterceptor(activityInterceptor)
                .addPathPatterns("/api/**")
                .order(100);
    }
}
