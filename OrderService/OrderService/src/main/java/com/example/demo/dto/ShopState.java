package com.example.demo.dto;

import java.util.List;

// Course ids on the user's wishlist, in the cart, and already owned (used by the heart / cart / "Enrolled" buttons).
public record ShopState(List<Integer> wishlist, List<Integer> cart, List<Integer> enrolled) {
}
