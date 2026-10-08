package com.tutorial.loadbalancer.strategy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.tutorial.model.Server;

@Component
public class RoundRobinStrategy implements LoadBalancingStrategy {

	private final AtomicInteger counter = new AtomicInteger();

	@Override
	public Server selectServer(List<Server> servers, List<String> excludedServers) {
		List<Server> availableServers = servers.stream().filter(server -> !excludedServers.contains(server.getUrl()))
				.toList();
		if(availableServers.isEmpty()) {
			throw new RuntimeException("No server is available for load balancing...");
		}
		
		int index = Math.floorMod(counter.getAndIncrement(), availableServers.size());
		return availableServers.get(index);
	}

}
