package com.tutorial.loadbalancer.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.tutorial.model.Server;

@Component
public class WeightedRoundRobinStrategy implements LoadBalancingStrategy {
	
	private final AtomicInteger counter = new AtomicInteger();

	@Override
	public Server selectServer(List<Server> servers, List<String> excludedServers) {
		List<Server> weightedServers = new ArrayList<>();
		
		for(Server server : servers) {
			if(excludedServers.contains(server.getUrl())) {
				continue;
			}
			
			for(int i = 0; i < server.getWeight(); i++) {
				weightedServers.add(server);
			}
		}

		if(weightedServers.isEmpty()) {
			throw new RuntimeException("No server is available for load balancing...");
		}
		
		int index = Math.floorMod(counter.getAndIncrement(), weightedServers.size());
		return weightedServers.get(index);
	}
	
}
