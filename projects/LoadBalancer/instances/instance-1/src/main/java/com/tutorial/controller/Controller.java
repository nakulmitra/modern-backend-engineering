package com.tutorial.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
	
	@Value("${instance.name}")
	private String instanceName;
	
	@GetMapping(value = "/hello", produces = MediaType.TEXT_PLAIN_VALUE)
	public String hello() {
		return "Response from " + instanceName;
	}
	
	@GetMapping(value = "/hi", produces = MediaType.TEXT_PLAIN_VALUE)
	public String hi() {
		return "Response from hi method with instance no: " + instanceName;
	}
	
	@GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
	public String health() {
		return "Server is up...";
	}
}
