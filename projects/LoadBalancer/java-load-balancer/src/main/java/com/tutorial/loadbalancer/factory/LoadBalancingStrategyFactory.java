package com.tutorial.loadbalancer.factory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tutorial.constants.LoadBalancingAlgorithm;
import com.tutorial.loadbalancer.strategy.LoadBalancingStrategy;
import com.tutorial.loadbalancer.strategy.RoundRobinStrategy;
import com.tutorial.loadbalancer.strategy.WeightedRoundRobinStrategy;

@Component
public class LoadBalancingStrategyFactory {

	@Value("${load-balancer.algorithm:ROUND_ROBIN}")
	private String algorithm;

	private RoundRobinStrategy rrStrategy;
	private WeightedRoundRobinStrategy wrrStrategy;

	public LoadBalancingStrategyFactory(RoundRobinStrategy rrStrategy, WeightedRoundRobinStrategy wrrStrategy) {
		this.rrStrategy = rrStrategy;
		this.wrrStrategy = wrrStrategy;
	}

	public LoadBalancingStrategy getStrategy() {
		LoadBalancingAlgorithm algo = LoadBalancingAlgorithm.valueOf(algorithm.toUpperCase());

		return switch (algo) {
			case ROUND_ROBIN -> rrStrategy;
			case WEIGHTED_ROUND_ROBIN -> wrrStrategy;
		};
	}

}