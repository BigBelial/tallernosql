package com.noSQL.claseJava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ClaseJavaApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClaseJavaApplication.class, args);
	}

}
