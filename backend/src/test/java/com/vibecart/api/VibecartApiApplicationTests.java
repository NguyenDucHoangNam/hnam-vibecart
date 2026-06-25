package com.vibecart.api;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.TimeZone;

@SpringBootTest
@Disabled("Requires a running PostgreSQL instance")
class VibecartApiApplicationTests {

	static {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
	}

	@Test
	void contextLoads() {
	}

}


