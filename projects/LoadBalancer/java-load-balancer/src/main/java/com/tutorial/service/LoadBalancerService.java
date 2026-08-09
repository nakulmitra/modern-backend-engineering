package com.tutorial.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.tutorial.model.Server;

@Service
public class LoadBalancerService {

	private final List<Server> servers = List.of(new Server("http://localhost:8080"),
			new Server("http://localhost:8081"));

	private final AtomicInteger counter = new AtomicInteger();

	private final RestClient client = RestClient.create();

	public String fwdRequest(String path) {
		int index = Math.floorMod(counter.getAndIncrement(), servers.size());
		Server server = servers.get(index);

		System.out.println("Server url: " + server.getUrl() + " is used...");
		try {
			return client.get().uri(server.getUrl() + path).retrieve().body(String.class);
		} catch (Exception ex) {
			System.err.println("Server " + server.getUrl() + " is DOWN....");
			
			for(int i = 0; i < servers.size(); i++) {
				if(!server.getUrl().equals(servers.get(i).getUrl())) {
					System.out.println("Forwarding request to new url " + servers.get(i).getUrl());
					return client.get().uri(servers.get(i).getUrl() + path).retrieve().body(String.class);
				}
			}
			
			throw new RuntimeException("No server is available...");
		}

	}
}