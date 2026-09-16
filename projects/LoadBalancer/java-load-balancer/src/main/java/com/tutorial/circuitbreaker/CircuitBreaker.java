package com.tutorial.circuitbreaker;

public class CircuitBreaker {

	public enum States {
		OPEN, CLOSED;
	}
	
	private States state = States.CLOSED;
	private final int failureThreshold;
	private final long openStateMillis;
	private long openedAt;
	private int failure;
	private final String serverUrl;
	
	public CircuitBreaker(String serverUrl, int failureThreshold, long openStateMillis) {
		this.serverUrl = serverUrl;
		this.failureThreshold = failureThreshold;
		this.openStateMillis = openStateMillis;
	}
	
	
	public synchronized boolean requestAllowed() {
		if(state == States.CLOSED) {
			return true;
		}
		
		//OPEN state
		long currentOpenDuration = System.currentTimeMillis() - openedAt;
		if(currentOpenDuration >= openStateMillis) {
			state = States.CLOSED;
			failure = 0;
			
			System.out.println("Server: " + serverUrl + " state has been updated from OPEN to CLOSED...");
			return true;
		}
		
		return false;
	}
	
	public synchronized void recordSuccess() {
		failure = 0;
	}
	
	public synchronized void recordFailure() {
		failure++;
		System.err.println("Server: " + serverUrl + " current failure count is: " + failure);
		
		if(failure >= failureThreshold) {
			state = States.OPEN;
			openedAt = System.currentTimeMillis();
			
			System.out.println("Server: " + serverUrl + " state has been updated from CLOSED to OPEN...");
		}
	}
	
}
