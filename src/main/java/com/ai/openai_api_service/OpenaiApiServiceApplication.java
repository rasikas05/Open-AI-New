package com.ai.openai_api_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpenaiApiServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpenaiApiServiceApplication.class, args);
        System.out.println(System.getenv("OPENAI_API_KEY"));
        System.out.println(" >>>>>>>>>>>>>>>>>>Open AI Application started <<<<<<<<<<<<<<<<<<<<<<<<<<< ");
	}
}
