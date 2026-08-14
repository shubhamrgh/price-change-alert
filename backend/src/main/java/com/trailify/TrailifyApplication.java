package com.trailify;

import java.security.Security;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TrailifyApplication {

    static {
        // web-push (jose4j VAPID signing) requires the BouncyCastle JCE provider
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    public static void main(String[] args) {
        SpringApplication.run(TrailifyApplication.class, args);
    }
}

