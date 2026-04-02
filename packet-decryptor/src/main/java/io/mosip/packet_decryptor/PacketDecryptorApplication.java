package io.mosip.packet_decryptor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = {"io.mosip.*", "io.mosip.kernel.auth.defaultadapter"})
@SpringBootApplication
public class PacketDecryptorApplication {

	public static void main(String[] args) {
		SpringApplication.run(PacketDecryptorApplication.class, args);
	}

}
