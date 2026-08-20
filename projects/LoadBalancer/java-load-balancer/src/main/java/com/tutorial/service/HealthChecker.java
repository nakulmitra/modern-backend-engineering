package com.tutorial.service;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.tutorial.model.Server;

@Component
public class HealthChecker {
	
	private RestClient client = RestClient.create();
	
	public boolean isHelathy(Server server) {
		System.out.println("Server: " + server.getUrl() + " getting called...");
		try {
			client.get().uri(server.getUrl() + "/health").retrieve().toBodilessEntity();
			return true;
		}catch(Exception ex) {
			System.err.println("Server: " + server.getUrl() + " is down...");
			return false;
		}
	}

}
