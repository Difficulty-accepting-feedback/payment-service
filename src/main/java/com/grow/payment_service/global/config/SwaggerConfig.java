package com.grow.payment_service.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI memberServiceOpenApi() {
		Info info = new Info()
			.title("Payment Service API 명세서")
			.version("v1.0")
			.description("Payment Service API 문서 입니다.")
			.contact(new Contact()
				.name("Grow")
				.email("temp@temp.com"))
			.license(new License().name("Apache 2.0").url("http://springdoc.org"));


		Server prodServer = new Server()
			.url("https://temp.com")
			.description("Production Server");

		Server localServer = new Server()
			.url("http://localhost:8081")
			.description("Local Server");

		return new OpenAPI()
			.info(info)
			.addServersItem(prodServer)
			.addServersItem(localServer)
			.components(new Components());
	}
}