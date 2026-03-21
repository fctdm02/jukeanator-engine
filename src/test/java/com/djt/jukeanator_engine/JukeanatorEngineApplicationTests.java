package com.djt.jukeanator_engine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JukeanatorEngineApplicationTests {

	@Test
	void contextLoads() {
	}

}
