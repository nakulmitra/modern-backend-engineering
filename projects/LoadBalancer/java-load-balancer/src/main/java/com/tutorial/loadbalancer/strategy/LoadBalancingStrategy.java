package com.tutorial.loadbalancer.strategy;

import java.util.List;

import com.tutorial.model.Server;

public interface LoadBalancingStrategy {
	
	Server selectServer(List<Server> servers, List<String> excludedServers);
	
}
