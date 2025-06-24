package io.mosip.ondemand_test_utility.config;

import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {


}
