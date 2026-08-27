package com.devluis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QmsApplication {

	public static void main(String[] args) {
		SpringApplication.run(QmsApplication.class, args);
	}

}
