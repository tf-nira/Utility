package io.mosip.ondemand_test_utility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(scanBasePackages = "io.mosip")
public class OndemandTestUtilityApplication {

	public static void main(String[] args) {
		SpringApplication.run(OndemandTestUtilityApplication.class, args);
	}
}
