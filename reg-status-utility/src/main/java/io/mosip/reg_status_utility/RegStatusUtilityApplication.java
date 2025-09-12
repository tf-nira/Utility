package io.mosip.reg_status_utility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = "io.mosip.reg_status_utility.repository")
@EntityScan(basePackages = "io.mosip.reg_status_utility.entity")
@ComponentScan(basePackages = {"io.mosip.*", "${mosip.auth.adapter.impl.basepackage}"})
@EnableTransactionManagement
public class RegStatusUtilityApplication {

	public static void main(String[] args) {
		SpringApplication.run(RegStatusUtilityApplication.class, args);
	}
}
