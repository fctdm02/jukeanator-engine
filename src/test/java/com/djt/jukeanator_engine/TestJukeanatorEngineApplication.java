package com.djt.jukeanator_engine;

import org.springframework.boot.SpringApplication;

public class TestJukeanatorEngineApplication {

	public static void main(String[] args) {
		SpringApplication.from(JukeANatorBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
