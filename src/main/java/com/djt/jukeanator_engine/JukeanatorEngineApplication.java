package com.djt.jukeanator_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.djt.jukeanator_engine.domain.songlibrary.service.config.SongLibraryProperties;

@SpringBootApplication
@EnableConfigurationProperties(SongLibraryProperties.class)
public class JukeanatorEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(JukeanatorEngineApplication.class, args);
	}
}