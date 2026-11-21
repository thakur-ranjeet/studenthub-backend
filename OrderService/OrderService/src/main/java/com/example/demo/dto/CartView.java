package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartView(List<ItemView> items, BigDecimal total, String currency, int count) {
}
