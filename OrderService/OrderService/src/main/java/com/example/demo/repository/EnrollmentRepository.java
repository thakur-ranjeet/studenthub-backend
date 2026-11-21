package com.example.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.Enrollment;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

	List<Enrollment> findByUsernameOrderByEnrolledAtDesc(String username);

	boolean existsByUsernameAndCourseId(String username, int courseId);

	// used when an order is refunded: the courses bought in that order are taken back
	@Transactional
	long deleteByOrderId(Long orderId);
}
