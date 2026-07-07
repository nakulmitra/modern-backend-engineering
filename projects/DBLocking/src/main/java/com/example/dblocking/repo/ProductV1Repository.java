package com.example.dblocking.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import com.example.dblocking.entity.ProductV1;

import jakarta.persistence.LockModeType;

@Repository
public interface ProductV1Repository extends JpaRepository<ProductV1, Long> {
	
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<ProductV1> findById(Long id);
}
