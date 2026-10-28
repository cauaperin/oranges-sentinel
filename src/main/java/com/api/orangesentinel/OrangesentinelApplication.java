package com.api.orangesentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrangesentinelApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrangesentinelApplication.class, args);
	}

}
