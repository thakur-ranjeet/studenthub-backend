package com.example.demo.dto;

import java.math.BigDecimal;

public record OrderItemView(int courseId, String cname, BigDecimal price) {
}
