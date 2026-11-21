package com.example.demo.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.client.CourseClient;
import com.example.demo.dto.CartView;
import com.example.demo.dto.CourseDto;
import com.example.demo.dto.ItemView;
import com.example.demo.dto.ShopState;
import com.example.demo.exception.ShopException;
import com.example.demo.model.CartItem;
import com.example.demo.model.Enrollment;
import com.example.demo.model.WishlistItem;
import com.example.demo.repository.CartRepository;
import com.example.demo.repository.EnrollmentRepository;
import com.example.demo.repository.WishlistRepository;

@Service
public class ShopService {

	private final WishlistRepository wishlist;
	private final CartRepository cart;
	private final CourseClient courses;
	private final EnrollmentRepository enrollments;

	public ShopService(WishlistRepository wishlist, CartRepository cart, CourseClient courses,
			EnrollmentRepository enrollments) {
		this.wishlist = wishlist;
		this.cart = cart;
		this.courses = courses;
		this.enrollments = enrollments;
	}

	// ---------- state (only ids, no call to the Course service) ----------

	public ShopState state(String user) {
		List<Integer> w = wishlist.findByUsernameOrderByCreatedAtDesc(user).stream().map(WishlistItem::getCourseId).toList();
		List<Integer> c = cart.findByUsernameOrderByCreatedAtDesc(user).stream().map(CartItem::getCourseId).toList();
		List<Integer> e = enrollments.findByUsernameOrderByEnrolledAtDesc(user).stream().map(Enrollment::getCourseId).toList();
		return new ShopState(w, c, e);
	}

	// ---------- wishlist ----------

	public ShopState addToWishlist(String user, int courseId) {
		requireCourse(courseId);
		if (!wishlist.existsByUsernameAndCourseId(user, courseId)) {
			try {
				wishlist.save(new WishlistItem(user, courseId));
			} catch (DataIntegrityViolationException ignored) {
				// double click / race: it is already there, which is what the user wanted
			}
		}
		return state(user);
	}

	public ShopState removeFromWishlist(String user, int courseId) {
		wishlist.deleteByUsernameAndCourseId(user, courseId);
		return state(user);
	}

	@Transactional
	public ShopState moveToCart(String user, int courseId) {
		rejectIfOwned(user, courseId);
		requirePurchasable(courseId);
		if (!cart.existsByUsernameAndCourseId(user, courseId)) {
			cart.save(new CartItem(user, courseId));
		}
		wishlist.deleteByUsernameAndCourseId(user, courseId);
		return state(user);
	}

	public List<ItemView> wishlistView(String user) {
		Map<Integer, CourseDto> all = courses.fetchAll();
		List<Integer> inCart = cart.findByUsernameOrderByCreatedAtDesc(user).stream().map(CartItem::getCourseId).toList();
		List<ItemView> out = new ArrayList<>();
		for (WishlistItem w : wishlist.findByUsernameOrderByCreatedAtDesc(user)) {
			CourseDto c = all.get(w.getCourseId());
			out.add(view(w.getCourseId(), c, inCart.contains(w.getCourseId()), w.getCreatedAt()));
		}
		return out;
	}

	// ---------- cart ----------

	public ShopState addToCart(String user, int courseId) {
		rejectIfOwned(user, courseId);
		requirePurchasable(courseId);
		if (!cart.existsByUsernameAndCourseId(user, courseId)) {
			try {
				cart.save(new CartItem(user, courseId));
			} catch (DataIntegrityViolationException ignored) {
				// already in the cart
			}
		}
		return state(user);
	}

	public ShopState removeFromCart(String user, int courseId) {
		cart.deleteByUsernameAndCourseId(user, courseId);
		return state(user);
	}

	public ShopState clearCart(String user) {
		cart.deleteByUsername(user);
		return state(user);
	}

	public CartView cartView(String user) {
		Map<Integer, CourseDto> all = courses.fetchAll();
		List<ItemView> items = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;
		for (CartItem i : cart.findByUsernameOrderByCreatedAtDesc(user)) {
			CourseDto c = all.get(i.getCourseId());
			ItemView v = view(i.getCourseId(), c, true, i.getCreatedAt());
			items.add(v);
			if (v.available()) {
				total = total.add(v.price()); // price comes from the Course service, never from the client
			}
		}
		return new CartView(items, total, "INR", items.size());
	}

	// ---------- helpers ----------

	private ItemView view(int courseId, CourseDto c, boolean inCart, java.time.Instant addedAt) {
		if (c == null) {
			return new ItemView(courseId, "Course #" + courseId, null, null, false, inCart, addedAt);
		}
		return new ItemView(courseId, c.getCname(), c.getDuration(), c.getPrice(), c.isForSale(), inCart, addedAt);
	}

	private CourseDto requireCourse(int courseId) {
		CourseDto c = courses.fetchAll().get(courseId);
		if (c == null) {
			throw new ShopException(HttpStatus.NOT_FOUND, "Course not found.");
		}
		return c;
	}

	private void rejectIfOwned(String user, int courseId) {
		if (enrollments.existsByUsernameAndCourseId(user, courseId)) {
			throw new ShopException(HttpStatus.CONFLICT, "You already own this course.");
		}
	}

	private void requirePurchasable(int courseId) {
		if (!requireCourse(courseId).isForSale()) {
			throw new ShopException(HttpStatus.BAD_REQUEST, "This course is not available for purchase yet.");
		}
	}
}
