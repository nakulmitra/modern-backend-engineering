package com.tutorial.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.tutorial.circuitbreaker.CircuitBreaker;
import com.tutorial.model.Server;

import jakarta.annotation.PostConstruct;

@Service
public class LoadBalancerService {

	private final List<Server> servers = List.of(new Server("http://localhost:8080", true),
			new Server("http://localhost:8081", true));

	private final AtomicInteger counter = new AtomicInteger();

	private final RestClient client;
	
	@Autowired
	private HealthChecker healthCheker;
	
	private static final int MAX_RETRY_ATTEMPTS = 1;
	
	private Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
	
	public LoadBalancerService() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(1_000);
		factory.setReadTimeout(2_000);
		
		client = RestClient.builder().requestFactory(factory).build();
	}
	
	@PostConstruct
	public void initilizedCircuitBreaker() {
		for(Server server: servers) {
			circuitBreakers.put(server.getUrl(), new CircuitBreaker(server.getUrl(), 3, 30_000));
		}
	}
	
	@Scheduled(fixedRate = 1800000)
	public void updateServerHealth() {
		for(Server server: servers) {
			boolean isHealthy = healthCheker.isHelathy(server);
			
			server.setHealthy(isHealthy);
			System.out.println("Updating the server: " + server.getUrl() + " is " + isHealthy);
		}
	}

	public String fwdRequest(String path) {
		List<Server> availableServers = servers.stream()
				.filter(Server::isHealthy)
				.filter(server -> circuitBreakers.get(server.getUrl()).requestAllowed())
				.toList();
		
		if(availableServers.isEmpty()) {
			return fallback();
		}
		
		int index = Math.floorMod(counter.getAndIncrement(), availableServers.size());
		int maxAttempts = Math.min(MAX_RETRY_ATTEMPTS + 1, availableServers.size());
		
		for(int attempt = 0; attempt < maxAttempts; attempt++) {
			int serverIndex = (attempt + index)%availableServers.size();
			Server server = availableServers.get(serverIndex);
			CircuitBreaker circuitBreaker = circuitBreakers.get(server.getUrl());
			
			System.out.println("Server: " + server.getUrl() + " is used...");
			try {
				String response = client.get().uri(server.getUrl() + path).retrieve().body(String.class);
				circuitBreaker.recordSuccess();
				return response;
			}catch(Exception ex) {
				System.err.println("Server: " + server.getUrl() + " has failed...");
				System.err.println("Exp message: " + ex.getMessage());
				circuitBreaker.recordFailure();
			}
		}
		
		return fallback();
	}
	
	private String fallback() {
		return "Service is currently unavailable. Please try again, after sometime...";
	}
}