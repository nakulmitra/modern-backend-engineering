package com.tutorial.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tutorial.service.LoadBalancerService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class LoadBalancerController {
	
	@Autowired
	private LoadBalancerService service;
	
	@RequestMapping(value = "/**", produces = MediaType.TEXT_PLAIN_VALUE)
	public String generic(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return service.fwdRequest(uri);
	}

	
}
