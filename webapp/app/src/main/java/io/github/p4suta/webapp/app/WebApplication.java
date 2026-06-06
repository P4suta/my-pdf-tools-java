package io.github.p4suta.webapp.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The pdfbook web front end entry point. */
@SpringBootApplication
@EnableScheduling
public class WebApplication {

    /**
     * Starts the web server.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
