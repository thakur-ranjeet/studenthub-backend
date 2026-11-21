package com.example.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.CartItem;

public interface CartRepository extends JpaRepository<CartItem, Long> {

	List<CartItem> findByUsernameOrderByCreatedAtDesc(String username);

	boolean existsByUsernameAndCourseId(String username, int courseId);

	@Transactional
	long deleteByUsernameAndCourseId(String username, int courseId);

	@Transactional
	long deleteByUsername(String username);
}
