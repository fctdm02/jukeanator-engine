package com.djt.jukeanator_engine;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class JukeANatorBackendApplication {

  public static void main(String[] args) {

    SpringApplicationBuilder builder = new SpringApplicationBuilder(JukeANatorBackendApplication.class);

    builder.initializers(context -> {

      if (context.getEnvironment().getProperty("app.ui-enabled", Boolean.class, false)) {

        System.setProperty("java.awt.headless", "false");
      }
    });

    builder.run(args);
  }
}
