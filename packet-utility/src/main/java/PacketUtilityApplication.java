

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@ComponentScan(
	    basePackages = {
	        "io.mosip.packet_utility.config",
	        "io.mosip", 
	        "io.mosip.kernel.core.logger.config", 
	        "${mosip.auth.adapter.impl.basepackage}"
	    },
	    excludeFilters = {
	        @ComponentScan.Filter(
	            type = FilterType.ASSIGNABLE_TYPE, 
	            classes = {io.mosip.commons.khazana.impl.PosixAdapter.class,io.mosip.commons.khazana.util.EncryptionHelper.class,io.mosip.commons.khazana.util.OfflineEncryptionUtil.class}
	        )
	    })
@SpringBootApplication
public class PacketUtilityApplication {

	public static void main(String[] args) {
		SpringApplication.run(PacketUtilityApplication.class, args);
	}

}
