package com.example.dblocking.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.dblocking.service.DBLockingService;

@RestController
@RequestMapping("/api")
public class DBLockingController {

	@Autowired
	DBLockingService service;

	@PostMapping(value = "/pessimistic-locking", produces = MediaType.TEXT_PLAIN_VALUE)
	public String pessimisticLocking(@RequestBody Long id) {
		try {
			return service.purchaseUsingPL(id);
		} catch (Exception e) {
			System.err.println("Exception at pessimisticLocking() due to: " + e.getMessage());
		}

		return "Sorry, product is out of stock...";
	}

	@PostMapping(value = "/optimistic-locking", produces = MediaType.TEXT_PLAIN_VALUE)
	public String optimisticLocking(@RequestBody Long id) {
		try {
			return service.purchaseUsingOL(id);
		} catch (Exception e) {
			System.err.println("Exception at optimisticLocking() due to: " + e.getMessage());
		}

		return "Sorry, product is out of stock...";
	}
}
