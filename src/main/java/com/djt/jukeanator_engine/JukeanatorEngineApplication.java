package com.djt.jukeanator_engine;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JukeanatorEngineApplication {

  public static void main(String[] args) {

    SpringApplicationBuilder builder = new SpringApplicationBuilder(JukeanatorEngineApplication.class);

    builder.initializers(context -> {

      if (context.getEnvironment().getProperty("user-interface.enabled", Boolean.class, false)) {

        System.setProperty("java.awt.headless", "false");
      }
    });

    builder.run(args);
  }
}
