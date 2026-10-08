package com.tutorial.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.tutorial.circuitbreaker.CircuitBreaker;
import com.tutorial.loadbalancer.factory.LoadBalancingStrategyFactory;
import com.tutorial.loadbalancer.strategy.LoadBalancingStrategy;
import com.tutorial.model.Server;

@Service
public class LoadBalancerService {

	@Value("${load-balancer.max-attempts:2}")
	private int MAX_ATTEMPTS;

	@Autowired
	private HealthChecker healthCheker;

	private final LoadBalancingStrategyFactory strategyFactory;
	private final RestClient client;
	private final List<Server> servers;
	private Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

	public String fwdRequest(String path) {
		List<Server> availableServers = getAvailableServers();
		if (availableServers.isEmpty()) {
			throw new RuntimeException("No server is available for load balancing...");
		}

		List<String> attemptedServers = new ArrayList<>();

		LoadBalancingStrategy strategy = strategyFactory.getStrategy();

		int maxAttempts = Math.min(MAX_ATTEMPTS, availableServers.size());

		for (int attepts = 0; attepts < maxAttempts; attepts++) {
			Server server = strategy.selectServer(availableServers, attemptedServers);
			attemptedServers.add(server.getUrl());

			System.out.println("Server: " + server.getUrl() + " has been used...");
			CircuitBreaker circuitBreaker = circuitBreakers.get(server.getUrl());

			try {
				String response = client.get().uri(server.getUrl() + path).retrieve().body(String.class);
				circuitBreaker.recordSuccess();
				return response;
			} catch (Exception ex) {
				System.err.println("Server: " + server.getUrl() + " has failed...");
				System.err.println("Exp message: " + ex.getMessage());
				circuitBreaker.recordFailure();
			}
		}

		throw new RuntimeException("Request to all available servers has been failed. Please try after some time...");
	}

	private List<Server> getAvailableServers() {
		return servers.stream().filter(Server::isHealthy)
				.filter(server -> circuitBreakers.get(server.getUrl()).requestAllowed()).toList();
	}

	public LoadBalancerService(LoadBalancingStrategyFactory strategyFactory) {
		this.strategyFactory = strategyFactory;

		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(1_000);
		factory.setReadTimeout(2_000);

		this.client = RestClient.builder().requestFactory(factory).build();

		this.servers = List.of(new Server("http://localhost:8080", true, 3),
				new Server("http://localhost:8081", true, 1));

		for (Server server : servers) {
			this.circuitBreakers.put(server.getUrl(), new CircuitBreaker(server.getUrl(), 3, 30_000));
		}
	}

	@Scheduled(fixedRate = 1800000)
	public void updateServerHealth() {
		for (Server server : servers) {
			boolean isHealthy = healthCheker.isHelathy(server);

			server.setHealthy(isHealthy);
			System.out.println("Updating the server: " + server.getUrl() + " is " + isHealthy);
		}
	}

}
