package edu.gatech.erms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point of the ERMS backend. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ErmsApplication {

  public static void main(String[] args) {
    SpringApplication.run(ErmsApplication.class, args);
  }
}
