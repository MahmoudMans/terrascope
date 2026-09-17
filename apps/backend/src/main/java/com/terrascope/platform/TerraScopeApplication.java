package com.terrascope.platform;

import com.terrascope.platform.config.UsgsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(UsgsProperties.class)
public class TerraScopeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TerraScopeApplication.class, args);
    }
}
