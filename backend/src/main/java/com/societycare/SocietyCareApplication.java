package com.societycare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduling drives the notification retention sweep; see NotificationRetentionJob. */
@SpringBootApplication
@EnableScheduling
public class SocietyCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocietyCareApplication.class, args);
    }
}
