package com.tutorial.model;

public class Server {

	private String url;
	private volatile boolean healthy;

	public Server(String url, boolean healthy) {
		this.url = url;
		this.healthy = healthy;
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
