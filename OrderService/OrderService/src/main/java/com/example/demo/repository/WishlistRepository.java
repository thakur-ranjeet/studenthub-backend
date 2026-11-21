package com.example.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.WishlistItem;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

	List<WishlistItem> findByUsernameOrderByCreatedAtDesc(String username);

	boolean existsByUsernameAndCourseId(String username, int courseId);

	@Transactional
	long deleteByUsernameAndCourseId(String username, int courseId);
}
