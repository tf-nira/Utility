package io.mosip.reg_status_utility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"io.mosip.*", "${mosip.auth.adapter.impl.basepackage}"})
public class RegStatusUtilityApplication {

	public static void main(String[] args) {
		SpringApplication.run(RegStatusUtilityApplication.class, args);
	}
}