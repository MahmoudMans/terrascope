package com.terrascope.platform.config;

import com.terrascope.platform.security.AdminSeeder;
import com.terrascope.platform.security.AuthInterceptor;
import com.terrascope.platform.security.AuthService;
import com.terrascope.platform.security.PasswordHasher;
import com.terrascope.platform.security.SecuritySettings;
import com.terrascope.platform.security.TokenService;
import com.terrascope.platform.security.UserAdminService;
import com.terrascope.platform.security.UserDirectory;
import com.terrascope.platform.security.UserStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class SecurityBeansConfiguration {

    @Bean
    SecuritySettings securitySettings(
            @Value("${terrascope.security.enabled:false}")
            boolean enabled,

            @Value("${terrascope.security.token-secret:}")
            String tokenSecret,

            @Value("${terrascope.security.token-ttl:PT8H}")
            String tokenTtl,

            @Value("${terrascope.security.admin-username:}")
            String adminUsername,

            @Value("${terrascope.security.admin-password:}")
            String adminPassword
    ) {
        return new SecuritySettings(
                enabled,
                tokenSecret,
                tokenTtl,
                adminUsername,
                adminPassword
        );
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new PasswordHasher(
                PasswordHasher.DEFAULT_ITERATIONS
        );
    }

    @Bean
    TokenService tokenService(
            SecuritySettings settings
    ) {
        String secret =
                settings.enabled()
                        ? settings.tokenSecret()
                        : UUID.randomUUID() + "" + UUID.randomUUID();

        return new TokenService(
                secret,
                settings.tokenTtl()
        );
    }

    @Bean
    UserDirectory userDirectory(
            UserStore store
    ) {
        return new UserDirectory(store);
    }

    @Bean
    AuthService authService(
            SecuritySettings settings,
            UserStore store,
            UserDirectory directory,
            PasswordHasher hasher,
            TokenService tokens
    ) {
        return new AuthService(
                settings,
                store,
                directory,
                hasher,
                tokens
        );
    }

    @Bean
    UserAdminService userAdminService(
            SecuritySettings settings,
            UserStore store,
            UserDirectory directory,
            PasswordHasher hasher
    ) {
        return new UserAdminService(
                settings,
                store,
                directory,
                hasher
        );
    }

    @Bean
    AuthInterceptor authInterceptor(
            SecuritySettings settings,
            AuthService authService
    ) {
        return new AuthInterceptor(
                settings,
                authService
        );
    }

    @Bean
    AdminSeeder adminSeeder(
            SecuritySettings settings,
            UserStore store,
            UserAdminService admins
    ) {
        return new AdminSeeder(
                settings,
                store,
                admins
        );
    }
}
