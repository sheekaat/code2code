package com.code2code.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.code2code.web", "com.code2code.service", "com.code2code.config"})
public class code2codeApplication {
    public static void main(String[] args) {
        SpringApplication.run(code2codeApplication.class, args);
    }
}
