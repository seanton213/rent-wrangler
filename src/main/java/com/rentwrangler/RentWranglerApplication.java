package com.rentwrangler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class RentWranglerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentWranglerApplication.class, args);
    }
}
