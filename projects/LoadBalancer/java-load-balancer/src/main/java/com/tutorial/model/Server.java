package com.tutorial.model;

public class Server {

	private String url;
	private volatile boolean healthy;
	private int weight;

	public Server(String url, boolean healthy) {
		this.url = url;
		this.healthy = healthy;
	}

	public Server(String url, boolean healthy, int weight) {
		this.url = url;
		this.healthy = healthy;
		this.weight = weight;
	}

	public int getWeight() {
		return weight;
	}

	public void setWeight(int weight) {
		this.weight = weight;
	}

	public boolean isHealthy() {
		return healthy;
	}

	public void setHealthy(boolean healthy) {
		this.healthy = healthy;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

}
