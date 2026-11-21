package com.example.demo.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.model.PurchaseOrderItem;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {

	List<PurchaseOrderItem> findByOrderId(Long orderId);

	List<PurchaseOrderItem> findByOrderIdIn(Collection<Long> orderIds);
}
