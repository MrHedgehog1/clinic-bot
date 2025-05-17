package com.clinicbot.clinic_bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories("com.clinicbot.clinic_bot.repository")
@EntityScan("com.clinicbot.clinic_bot.model")
public class ClinicBotApplication {

	public static void main(String[] args) {

		SpringApplication.run(ClinicBotApplication.class, args);
	}

}
