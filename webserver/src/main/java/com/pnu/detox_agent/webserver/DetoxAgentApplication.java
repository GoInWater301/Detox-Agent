package com.pnu.detox_agent.webserver;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DetoxAgentApplication {

    public static void main(String[] args) {
        // Load .env file from the project root or current directory
        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMissing()
                .load();

        // Set properties so they can be accessed via @Value or ${} in properties files
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });

        SpringApplication.run(DetoxAgentApplication.class, args);
    }

}
