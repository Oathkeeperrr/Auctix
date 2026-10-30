package com.auctix;

import com.auctix.config.AuctixProperties;
import com.auctix.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AuctixProperties.class, JwtProperties.class})
public class AuctixApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuctixApplication.class, args);
    }
}
