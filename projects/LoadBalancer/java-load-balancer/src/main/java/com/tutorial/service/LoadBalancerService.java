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
	
	@Scheduled(fixedRate = 60000)
	public void updateServerHealth() {
		for(Server server: servers) {
			boolean isHealthy = healthCheker.isHelathy(server);
			
			server.setHealthy(isHealthy);
			System.out.println("Updating the server: " + server.getUrl() + " is " + isHealthy);
		}
	}

	public String fwdRequest(String path) {
		List<Server> helathyServers = servers.stream().filter(Server::isHealthy).toList();
		if(helathyServers.isEmpty()) {
			throw new RuntimeException("No healthy server is available...");
		}
		
		
		int index = Math.floorMod(counter.getAndIncrement(), helathyServers.size());
		Server server = helathyServers.get(index);

		System.out.println("Server url: " + server.getUrl() + " is used...");
		try {
			return client.get().uri(server.getUrl() + path).retrieve().body(String.class);
		} catch (Exception ex) {
			System.err.println("Server " + server.getUrl() + " is DOWN....");
			
			for(int i = 0; i < helathyServers.size(); i++) {
				if(!server.getUrl().equals(helathyServers.get(i).getUrl())) {
					System.out.println("Forwarding request to new url " + servers.get(i).getUrl());
					return client.get().uri(servers.get(i).getUrl() + path).retrieve().body(String.class);
				}
			}
			
			throw new RuntimeException("No server is available...");
		}

	}
}