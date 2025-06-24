package io.mosip.packet_utility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = {"io.mosip.*", "${mosip.auth.adapter.impl.basepackage}"})
@SpringBootApplication
public class PacketUtilityApplication {

	public static void main(String[] args) {
		SpringApplication.run(PacketUtilityApplication.class, args);
	}

}
