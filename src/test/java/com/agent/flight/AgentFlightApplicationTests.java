package com.agent.flight;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"cv.openai.api-key=test-key", "cv.runner.enabled=false"})
class AgentFlightApplicationTests {

	@Test
	void contextLoads() {
	}

}
