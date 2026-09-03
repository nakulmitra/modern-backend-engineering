package com.tutorial.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.tutorial.model.Server;

@Service
public class LoadBalancerService {

	private final List<Server> servers = List.of(new Server("http://localhost:8080", true),
			new Server("http://localhost:8081", true));

	private final AtomicInteger counter = new AtomicInteger();

	private final RestClient client = RestClient.create();
	
	@Autowired
	private HealthChecker healthCheker;
	
	private static final int MAX_RETRY_ATTEMPTS = 1;
	
	@Scheduled(fixedRate = 180000)
	public void updateServerHealth() {
		for(Server server: servers) {
			boolean isHealthy = healthCheker.isHelathy(server);
			
			server.setHealthy(isHealthy);
			System.out.println("Updating the server: " + server.getUrl() + " is " + isHealthy);
		}
	}

	public String fwdRequest(String path) {
		List<Server> healthyServers = servers.stream().filter(Server::isHealthy).toList();
		if(healthyServers.isEmpty()) {
			return fallback();
		}
		
		int index = Math.floorMod(counter.getAndIncrement(), healthyServers.size());
		int maxAttempts = Math.min(MAX_RETRY_ATTEMPTS + 1, healthyServers.size());
		
		for(int attempt = 0; attempt < maxAttempts; attempt++) {
			int serverIndex = (attempt + index)%healthyServers.size();
			Server server = healthyServers.get(serverIndex);
			
			System.out.println("Server: " + server.getUrl() + " is used...");
			try {
				return client.get().uri(server.getUrl() + path).retrieve().body(String.class);
			}catch(Exception ex) {
				System.err.println("Server: " + server.getUrl() + " has failed...");
			}
		}
		
		return fallback();
	}
	
	private String fallback() {
		return "Service is currently unavailable. Please try again, after sometime...";
	}
}