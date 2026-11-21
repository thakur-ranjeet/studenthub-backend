package com.example.demo.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.client.CourseClient;
import com.example.demo.client.RazorpayClient;
import com.example.demo.client.RazorpayClient.RefundResult;
import com.example.demo.dto.CheckoutResponse;
import com.example.demo.dto.ConfigView;
import com.example.demo.dto.CourseDto;
import com.example.demo.dto.EnrollmentView;
import com.example.demo.dto.OrderItemView;
import com.example.demo.dto.OrderView;
import com.example.demo.dto.ReceiptView;
import com.example.demo.dto.VerifyRequest;
import com.example.demo.exception.ShopException;
import com.example.demo.model.CartItem;
import com.example.demo.model.Enrollment;
import com.example.demo.model.OrderStatus;
import com.example.demo.model.PurchaseOrder;
import com.example.demo.model.PurchaseOrderItem;
import com.example.demo.model.WebhookEvent;
import com.example.demo.repository.CartRepository;
import com.example.demo.repository.EnrollmentRepository;
import com.example.demo.repository.PurchaseOrderItemRepository;
import com.example.demo.repository.PurchaseOrderRepository;
import com.example.demo.repository.WebhookEventRepository;
import com.example.demo.repository.WishlistRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PurchaseService {

	private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);
	private static final String CURRENCY = "INR";

	private final PurchaseOrderRepository orders;
	private final PurchaseOrderItemRepository orderItems;
	private final EnrollmentRepository enrollments;
	private final CartRepository cart;
	private final WishlistRepository wishlist;
	private final WebhookEventRepository webhookEvents;
	private final CourseClient courses;
	private final RazorpayClient razorpay;
	private final ObjectMapper mapper;

	public PurchaseService(PurchaseOrderRepository orders, PurchaseOrderItemRepository orderItems,
			EnrollmentRepository enrollments, CartRepository cart, WishlistRepository wishlist,
			WebhookEventRepository webhookEvents, CourseClient courses, RazorpayClient razorpay, ObjectMapper mapper) {
		this.orders = orders;
		this.orderItems = orderItems;
		this.enrollments = enrollments;
		this.cart = cart;
		this.wishlist = wishlist;
		this.webhookEvents = webhookEvents;
		this.courses = courses;
		this.razorpay = razorpay;
		this.mapper = mapper;
	}

	public ConfigView config() {
		return new ConfigView(razorpay.getKeyId(), razorpay.isTestMode() ? "test" : "live");
	}

	private record Line(int courseId, String name, long paise) {
	}

	// ======================================================================================
	// checkout: build the order from the SERVER's cart and the SERVER's prices
	// ======================================================================================

	public CheckoutResponse checkout(String user) {
		List<CartItem> cartItems = cart.findByUsernameOrderByCreatedAtDesc(user);
		if (cartItems.isEmpty()) {
			throw new ShopException(HttpStatus.BAD_REQUEST, "Your cart is empty.");
		}
		Map<Integer, CourseDto> all = courses.fetchAll();

		List<Line> lines = new ArrayList<>();
		long total = 0;
		for (CartItem item : cartItems) {
			CourseDto course = all.get(item.getCourseId());
			if (course == null || !course.isForSale()) {
				throw new ShopException(HttpStatus.CONFLICT,
						"A course in your cart is no longer available. Remove it and try again.");
			}
			if (enrollments.existsByUsernameAndCourseId(user, item.getCourseId())) {
				throw new ShopException(HttpStatus.CONFLICT,
						"You already own \"" + course.getCname() + "\". Remove it from your cart.");
			}
			long paise = toPaise(course.getPrice());
			lines.add(new Line(course.getCid(), course.getCname(), paise));
			total += paise;
		}

		PurchaseOrder order = orders.save(new PurchaseOrder(user, total, CURRENCY));
		String razorpayOrderId;
		try {
			razorpayOrderId = razorpay.createOrder(total, CURRENCY, "SH-" + order.getId());
		} catch (RuntimeException e) {
			orders.delete(order); // nothing was created at Razorpay, do not leave a dangling order
			throw e;
		}
		order.setRazorpayOrderId(razorpayOrderId);
		orders.save(order);
		for (Line line : lines) {
			orderItems.save(new PurchaseOrderItem(order.getId(), line.courseId(), line.name(), line.paise()));
		}
		return new CheckoutResponse(order.getId(), razorpayOrderId, total, CURRENCY, razorpay.getKeyId(), lines.size());
	}

	// ======================================================================================
	// payment confirmation, path 1: the browser calls verify after the Razorpay window succeeds
	// ======================================================================================

	@Transactional
	public OrderView verify(String user, VerifyRequest request) {
		if (request == null || isBlank(request.razorpayOrderId()) || isBlank(request.razorpayPaymentId())
				|| isBlank(request.razorpaySignature())) {
			throw new ShopException(HttpStatus.BAD_REQUEST, "Payment details are missing.");
		}
		PurchaseOrder order = orders.findByRazorpayOrderId(request.razorpayOrderId())
				.filter(o -> o.getUsername().equals(user))
				.orElseThrow(() -> new ShopException(HttpStatus.NOT_FOUND, "Order not found."));

		if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.REFUNDED) {
			return views(List.of(order)).get(0); // already confirmed (maybe by the webhook): verifying twice is harmless
		}
		// use the order id stored on OUR server, never the one the browser sent
		if (!razorpay.verifySignature(order.getRazorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature())) {
			log.warn("Invalid payment signature for order {} (user {})", order.getId(), user);
			throw new ShopException(HttpStatus.BAD_REQUEST,
					"The payment could not be verified, so nothing was unlocked. If money was deducted, quote order #"
							+ order.getId() + " to support.");
		}
		fulfill(order, request.razorpayPaymentId());
		return views(List.of(order)).get(0);
	}

	// Mark the order paid and give the buyer the courses. Used by both confirmation paths.
	private void fulfill(PurchaseOrder order, String paymentId) {
		order.setStatus(OrderStatus.PAID);
		order.setRazorpayPaymentId(paymentId);
		order.setPaidAt(Instant.now());
		orders.save(order);
		for (PurchaseOrderItem item : orderItems.findByOrderId(order.getId())) {
			if (!enrollments.existsByUsernameAndCourseId(order.getUsername(), item.getCourseId())) {
				enrollments.save(new Enrollment(order.getUsername(), item.getCourseId(), item.getCourseName(), order.getId()));
			}
			cart.deleteByUsernameAndCourseId(order.getUsername(), item.getCourseId());
			wishlist.deleteByUsernameAndCourseId(order.getUsername(), item.getCourseId());
		}
	}

	// ======================================================================================
	// payment confirmation, path 2: Razorpay calls us (works even if the browser was closed)
	// ======================================================================================

	@Transactional
	public WebhookResult handleWebhook(byte[] rawBody, String signature, String eventId) {
		if (!razorpay.isWebhookConfigured()) {
			return WebhookResult.NOT_CONFIGURED;
		}
		// 1) authenticity: HMAC over the RAW bytes with the webhook secret
		if (!razorpay.verifyWebhookSignature(rawBody, signature)) {
			log.warn("Webhook rejected: bad signature");
			return WebhookResult.BAD_SIGNATURE;
		}
		// 2) idempotency: Razorpay delivers at least once, so repeats must change nothing
		if (!isBlank(eventId) && webhookEvents.existsByEventId(eventId)) {
			return WebhookResult.DUPLICATE;
		}
		JsonNode root;
		try {
			root = mapper.readTree(rawBody);
		} catch (IOException e) {
			return WebhookResult.BAD_PAYLOAD;
		}
		String event = root.path("event").asText("");
		switch (event) {
		case "payment.captured", "order.paid" -> onPaid(root);
		case "refund.processed" -> onRefund(root, true);
		case "refund.failed" -> onRefund(root, false);
		default -> log.info("Webhook event {} ignored", event);
		}
		if (!isBlank(eventId)) {
			webhookEvents.save(new WebhookEvent(eventId, event.isBlank() ? "unknown" : event));
		}
		return WebhookResult.OK;
	}

	private void onPaid(JsonNode root) {
		JsonNode payment = root.path("payload").path("payment").path("entity");
		String razorpayOrderId = payment.path("order_id").asText("");
		String paymentId = payment.path("id").asText("");
		if (razorpayOrderId.isBlank() || paymentId.isBlank() || !"captured".equals(payment.path("status").asText(""))) {
			log.info("Webhook payment event ignored (not a captured payment)");
			return;
		}
		Optional<PurchaseOrder> found = orders.findByRazorpayOrderId(razorpayOrderId);
		if (found.isEmpty()) {
			log.warn("Webhook for an unknown Razorpay order {}", razorpayOrderId);
			return;
		}
		PurchaseOrder order = found.get();
		if (order.getStatus() != OrderStatus.CREATED) {
			return; // already paid or refunded
		}
		// never trust the event alone: the amount and currency must match what WE created
		if (payment.path("amount").asLong(-1) != order.getAmountPaise()
				|| !order.getCurrency().equals(payment.path("currency").asText(""))) {
			log.error("Webhook amount or currency does not match order {}; not fulfilled", order.getId());
			return;
		}
		fulfill(order, paymentId);
		log.info("Order {} confirmed by webhook", order.getId());
	}

	private void onRefund(JsonNode root, boolean processed) {
		JsonNode refund = root.path("payload").path("refund").path("entity");
		String refundId = refund.path("id").asText("");
		String paymentId = refund.path("payment_id").asText("");
		if (refundId.isBlank() || paymentId.isBlank()) {
			return;
		}
		Optional<PurchaseOrder> found = orders.findByRazorpayPaymentId(paymentId);
		if (found.isEmpty()) {
			log.warn("Refund webhook for an unknown payment {}", paymentId);
			return;
		}
		PurchaseOrder order = found.get();
		if (order.getStatus() != OrderStatus.PAID) {
			return; // nothing to do (never paid, or already refunded)
		}
		if (order.getRefundId() == null) {
			order.setRefundId(refundId); // refund made directly in the Razorpay dashboard
		} else if (!order.getRefundId().equals(refundId)) {
			log.warn("Refund webhook {} does not belong to order {}", refundId, order.getId());
			return;
		}
		if (!processed) {
			order.setRefundStatus("failed");
			orders.save(order);
		} else if (refund.path("amount").asLong(-1) == order.getAmountPaise()) {
			completeRefund(order);
		} else {
			log.warn("Partial refund on order {} ignored (only full refunds are supported)", order.getId());
		}
	}

	// ======================================================================================
	// refunds (administrators): always the whole order
	// ======================================================================================

	@Transactional
	public OrderView refundOrder(long orderId, String user, boolean admin) {
		PurchaseOrder order = orders.findById(orderId)
				.orElseThrow(() -> new ShopException(HttpStatus.NOT_FOUND, "Order not found."));
		if (!admin && !order.getUsername().equals(user)) {
			throw new ShopException(HttpStatus.NOT_FOUND, "Order not found.");
		}
		if (order.getStatus() != OrderStatus.PAID) {
			throw new ShopException(HttpStatus.CONFLICT, "Only paid orders can be refunded.");
		}
		if (order.getRefundId() != null) {
			throw new ShopException(HttpStatus.CONFLICT, "A refund was already started for this order.");
		}

		// Step 1: Student requests a refund -> mark as "requested" (pending admin approval)
		if (!admin) {
			if ("requested".equals(order.getRefundStatus())) {
				throw new ShopException(HttpStatus.CONFLICT, "Your refund request is already waiting for admin approval.");
			}
			order.setRefundStatus("requested");
			orders.save(order);
			log.info("Refund requested by user {} for order {}", user, order.getId());
			return views(List.of(order)).get(0);
		}

		// Step 2: Admin approves the refund -> process via Razorpay and revoke course access
		if (isBlank(order.getRazorpayPaymentId())) {
			throw new ShopException(HttpStatus.CONFLICT, "This order has no payment to refund.");
		}
		RefundResult result = razorpay.refund(order.getRazorpayPaymentId(), order.getAmountPaise(), order.getId());
		order.setRefundId(result.id());
		applyRefundStatus(order, result.status());
		log.info("Refund {} approved and processed for order {} with status {}", result.id(), order.getId(), result.status());
		return views(List.of(order)).get(0);
	}

	// Asks Razorpay for the current refund state (useful while it is still "pending", or when no webhook is set up).
	@Transactional
	public OrderView syncRefund(long orderId, String user, boolean admin) {
		PurchaseOrder order = orders.findById(orderId)
				.orElseThrow(() -> new ShopException(HttpStatus.NOT_FOUND, "Order not found."));
		if (!admin && !order.getUsername().equals(user)) {
			throw new ShopException(HttpStatus.NOT_FOUND, "Order not found.");
		}
		if (order.getRefundId() == null) {
			if ("requested".equals(order.getRefundStatus())) {
				return views(List.of(order)).get(0); // still waiting for admin approval
			}
			throw new ShopException(HttpStatus.CONFLICT, "No refund was started for this order.");
		}
		if (order.getStatus() != OrderStatus.REFUNDED) {
			RefundResult result = razorpay.fetchRefund(order.getRazorpayPaymentId(), order.getRefundId());
			applyRefundStatus(order, result.status());
		}
		return views(List.of(order)).get(0);
	}

	private void applyRefundStatus(PurchaseOrder order, String status) {
		if ("processed".equals(status)) {
			completeRefund(order);
		} else {
			order.setRefundStatus("failed".equals(status) ? "failed" : "pending");
			orders.save(order);
		}
	}

	// the money is back with the customer: close the order and take the courses back
	private void completeRefund(PurchaseOrder order) {
		order.setStatus(OrderStatus.REFUNDED);
		order.setRefundStatus("processed");
		order.setRefundedAt(Instant.now());
		orders.save(order);
		enrollments.deleteByOrderId(order.getId());
	}

	// ======================================================================================
	// receipts
	// ======================================================================================

	public ReceiptView receipt(long orderId, String user, boolean admin) {
		PurchaseOrder order = orders.findById(orderId).orElseThrow(() -> new ShopException(HttpStatus.NOT_FOUND, "Order not found."));
		if (!admin && !order.getUsername().equals(user)) {
			throw new ShopException(HttpStatus.NOT_FOUND, "Order not found."); // do not reveal other people's orders
		}
		if (order.getPaidAt() == null) {
			throw new ShopException(HttpStatus.CONFLICT, "A receipt is available only for paid orders.");
		}
		OrderView v = views(List.of(order)).get(0);
		String number = "SH-" + order.getPaidAt().atZone(ZoneOffset.UTC).getYear() + "-" + String.format("%06d", order.getId());
		return new ReceiptView(number, order.getId(), order.getRazorpayOrderId(), order.getRazorpayPaymentId(),
				order.getUsername(), order.getPaidAt(), v.total(), v.currency(), v.status(), v.refundStatus(),
				order.getRefundedAt(), razorpay.isTestMode(), v.items());
	}

	// ======================================================================================
	// reading
	// ======================================================================================

	public List<OrderView> myOrders(String user) {
		return views(orders.findByUsernameOrderByCreatedAtDesc(user));
	}

	public List<OrderView> allOrders() {
		return views(orders.findAllByOrderByCreatedAtDesc());
	}

	public List<EnrollmentView> myCourses(String user) {
		Map<Integer, CourseDto> all = courses.fetchAll();
		List<EnrollmentView> out = new ArrayList<>();
		for (Enrollment e : enrollments.findByUsernameOrderByEnrolledAtDesc(user)) {
			CourseDto c = all.get(e.getCourseId());
			out.add(new EnrollmentView(e.getCourseId(), c != null ? c.getCname() : e.getCourseName(),
					c != null ? c.getDuration() : null, e.getEnrolledAt(), e.getOrderId()));
		}
		return out;
	}

	// ---------- helpers ----------

	private List<OrderView> views(List<PurchaseOrder> list) {
		if (list.isEmpty()) {
			return List.of();
		}
		Map<Long, List<PurchaseOrderItem>> itemsByOrder = orderItems
				.findByOrderIdIn(list.stream().map(PurchaseOrder::getId).toList()).stream()
				.collect(Collectors.groupingBy(PurchaseOrderItem::getOrderId));
		List<OrderView> out = new ArrayList<>();
		for (PurchaseOrder o : list) {
			List<OrderItemView> items = itemsByOrder.getOrDefault(o.getId(), List.of()).stream()
					.map(i -> new OrderItemView(i.getCourseId(), i.getCourseName(), BigDecimal.valueOf(i.getPricePaise(), 2)))
					.toList();
			out.add(new OrderView(o.getId(), o.getRazorpayOrderId(), o.getRazorpayPaymentId(), o.getUsername(),
					BigDecimal.valueOf(o.getAmountPaise(), 2), o.getCurrency(), o.getStatus().name(), o.getRefundStatus(),
					o.getCreatedAt(), o.getPaidAt(), o.getRefundedAt(), items));
		}
		return out;
	}

	private static long toPaise(BigDecimal rupees) {
		return rupees.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
