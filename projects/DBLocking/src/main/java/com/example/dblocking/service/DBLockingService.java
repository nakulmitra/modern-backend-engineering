package com.example.dblocking.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dblocking.entity.ProductV1;
import com.example.dblocking.entity.ProductV2;
import com.example.dblocking.repo.ProductV1Repository;
import com.example.dblocking.repo.ProductV2Repository;

@Service
public class DBLockingService {

	@Autowired
	ProductV1Repository repoV1;

	@Autowired
	ProductV2Repository repoV2;

	@Transactional
	public String purchaseUsingPL(Long id) throws Exception {
		ProductV1 product = repoV1.findById(id).orElseThrow(() -> new RuntimeException("Product not found..."));

		if (product.getQuantity() <= 0) {
			throw new RuntimeException("Product is out of stock...");
		}

		Thread.sleep(10000);

		product.setQuantity(product.getQuantity() - 1);
		repoV1.save(product);

		return "Your order has been placed...";
	}

	@Transactional
	public String purchaseUsingOL(Long id) throws Exception {
		ProductV2 product = repoV2.findById(id).orElseThrow(() -> new RuntimeException("Product not found..."));

		if (product.getQuantity() <= 0) {
			throw new RuntimeException("Product is out of stock...");
		}

		Thread.sleep(10000);

		product.setQuantity(product.getQuantity() - 1);
		repoV2.save(product);

		return "Your order has been placed...";
	}

}
