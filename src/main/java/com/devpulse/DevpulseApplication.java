package com.devpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * THE entry point from Phase 2 onward.
 *
 * @SpringBootApplication = three annotations in one:
 *   @Configuration            this class can declare @Bean methods
 *   @EnableAutoConfiguration  Spring Boot looks at the jars on the classpath and configures
 *                             things for you (web server because webmvc is present, a DataSource
 *                             because H2 + jdbc are present, JSON because Jackson is present...)
 *   @ComponentScan            find every @Component/@Service/@RestController/@Repository/
 *                             @Configuration in com.devpulse.** and register it as a bean
 *
 * The Phase 1 'engine' package has NO Spring annotations, so scanning ignores it. We turn
 * those plain classes into beans explicitly in config/EngineConfiguration.
 */
@SpringBootApplication
public class DevpulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevpulseApplication.class, args);
    }
}
