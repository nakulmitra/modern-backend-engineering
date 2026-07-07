package com.example.dblocking.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.dblocking.entity.ProductV2;

@Repository
public interface ProductV2Repository extends JpaRepository<ProductV2, Long> {
	
}
