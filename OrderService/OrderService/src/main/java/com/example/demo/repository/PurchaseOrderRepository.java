package com.example.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.model.PurchaseOrder;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

	Optional<PurchaseOrder> findByRazorpayOrderId(String razorpayOrderId);

	Optional<PurchaseOrder> findByRazorpayPaymentId(String razorpayPaymentId);

	List<PurchaseOrder> findByUsernameOrderByCreatedAtDesc(String username);

	List<PurchaseOrder> findAllByOrderByCreatedAtDesc();
}
