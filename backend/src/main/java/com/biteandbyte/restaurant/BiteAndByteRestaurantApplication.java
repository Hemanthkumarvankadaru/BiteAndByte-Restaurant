package com.biteandbyte.restaurant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BiteAndByteRestaurantApplication {

	public static void main(String[] args) {
		SpringApplication.run(BiteAndByteRestaurantApplication.class, args);
	}

}
