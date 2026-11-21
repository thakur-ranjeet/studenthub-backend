package com.example.demo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.CartView;
import com.example.demo.dto.CheckoutResponse;
import com.example.demo.dto.ConfigView;
import com.example.demo.dto.EnrollmentView;
import com.example.demo.dto.OrderView;
import com.example.demo.dto.ReceiptView;
import com.example.demo.dto.VerifyRequest;
import com.example.demo.exception.ShopException;
import com.example.demo.service.PurchaseService;
import com.example.demo.dto.ItemView;
import com.example.demo.dto.ShopState;
import com.example.demo.service.ShopService;

// The signed-in user comes from the verified JWT (set by JwtAuthFilter), never from the request body.
@RestController
@RequestMapping("/api/v1/shop")
public class ShopController {

	private final ShopService shop;
	private final PurchaseService purchases;

	public ShopController(ShopService shop, PurchaseService purchases) {
		this.shop = shop;
		this.purchases = purchases;
	}

	// http://localhost:8083/api/v1/shop/state
	@GetMapping("/state")
	public ShopState state(@RequestAttribute("username") String user) {
		return shop.state(user);
	}

	// ----- wishlist -----
	@GetMapping("/wishlist")
	public List<ItemView> wishlist(@RequestAttribute("username") String user) {
		return shop.wishlistView(user);
	}

	@PostMapping("/wishlist/{courseId}")
	public ShopState addToWishlist(@RequestAttribute("username") String user, @PathVariable int courseId) {
		return shop.addToWishlist(user, courseId);
	}

	@DeleteMapping("/wishlist/{courseId}")
	public ShopState removeFromWishlist(@RequestAttribute("username") String user, @PathVariable int courseId) {
		return shop.removeFromWishlist(user, courseId);
	}

	@PostMapping("/wishlist/{courseId}/move-to-cart")
	public ShopState moveToCart(@RequestAttribute("username") String user, @PathVariable int courseId) {
		return shop.moveToCart(user, courseId);
	}

	// ----- cart -----
	@GetMapping("/cart")
	public CartView cart(@RequestAttribute("username") String user) {
		return shop.cartView(user);
	}

	@PostMapping("/cart/{courseId}")
	public ShopState addToCart(@RequestAttribute("username") String user, @PathVariable int courseId) {
		return shop.addToCart(user, courseId);
	}

	@DeleteMapping("/cart/{courseId}")
	public ShopState removeFromCart(@RequestAttribute("username") String user, @PathVariable int courseId) {
		return shop.removeFromCart(user, courseId);
	}

	@DeleteMapping("/cart")
	public ShopState clearCart(@RequestAttribute("username") String user) {
		return shop.clearCart(user);
	}

	// ----- checkout and payment -----
	@GetMapping("/config")
	public ConfigView config() {
		return purchases.config();
	}

	// creates the order on the server (price from the Course service) and returns what the Razorpay window needs
	@PostMapping("/checkout")
	public CheckoutResponse checkout(@RequestAttribute("username") String user) {
		return purchases.checkout(user);
	}

	// called after Razorpay Checkout succeeds; unlocks the courses only if the signature is valid
	@PostMapping("/checkout/verify")
	public OrderView verify(@RequestAttribute("username") String user, @RequestBody VerifyRequest request) {
		return purchases.verify(user, request);
	}

	@GetMapping("/orders")
	public List<OrderView> orders(@RequestAttribute("username") String user) {
		return purchases.myOrders(user);
	}

	@GetMapping("/my-courses")
	public List<EnrollmentView> myCourses(@RequestAttribute("username") String user) {
		return purchases.myCourses(user);
	}

	// every order of every user: administrators only
	@GetMapping("/admin/orders")
	public List<OrderView> adminOrders(@RequestAttribute(value = "role", required = false) String role) {
		requireAdmin(role);
		return purchases.allOrders();
	}

	// printable receipt of a paid order: the owner or an administrator
	@GetMapping("/orders/{id}/receipt")
	public ReceiptView receipt(@RequestAttribute("username") String user,
			@RequestAttribute(value = "role", required = false) String role, @PathVariable long id) {
		return purchases.receipt(id, user, "ROLE_ADMIN".equals(role));
	}

	// student requests a refund (goes to admin for approval)
		@PostMapping("/orders/{id}/refund")
		public OrderView refundMyOrder(@RequestAttribute("username") String user, @PathVariable long id) {
			return purchases.refundOrder(id, user, false);
		}

		// student checks refund status
		@PostMapping("/orders/{id}/refund/sync")
		public OrderView syncMyRefund(@RequestAttribute("username") String user, @PathVariable long id) {
			return purchases.syncRefund(id, user, false);
		}

		// full refund / approval of a paid order: administrators only
		@PostMapping("/admin/orders/{id}/refund")
		public OrderView refund(@RequestAttribute("username") String user,
				@RequestAttribute(value = "role", required = false) String role, @PathVariable long id) {
			requireAdmin(role);
			return purchases.refundOrder(id, user, true);
		}

		// asks Razorpay for the latest refund state: administrators only
		@PostMapping("/admin/orders/{id}/refund/sync")
		public OrderView syncRefund(@RequestAttribute("username") String user,
				@RequestAttribute(value = "role", required = false) String role, @PathVariable long id) {
			requireAdmin(role);
			return purchases.syncRefund(id, user, true);
		}

	private static void requireAdmin(String role) {
		if (!"ROLE_ADMIN".equals(role)) {
			throw new ShopException(HttpStatus.FORBIDDEN, "Only administrators can do this.");
		}
	}
}
