package com.finance.forecast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ForecastServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ForecastServiceApplication.class, args);
    }
}
