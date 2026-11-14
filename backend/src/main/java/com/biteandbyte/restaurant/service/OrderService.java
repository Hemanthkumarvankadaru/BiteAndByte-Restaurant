package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.dto.PageResponse;
import com.biteandbyte.restaurant.dto.order.OrderResponse;
import com.biteandbyte.restaurant.dto.order.OrderSummaryResponse;
import com.biteandbyte.restaurant.dto.order.PlaceOrderRequest;
import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.exception.ResourceNotFoundException;
import com.biteandbyte.restaurant.model.Address;
import com.biteandbyte.restaurant.model.CartItem;
import com.biteandbyte.restaurant.model.CustomerOrder;
import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.model.OrderItem;
import com.biteandbyte.restaurant.model.OrderStatus;
import com.biteandbyte.restaurant.model.Payment;
import com.biteandbyte.restaurant.model.PaymentMethod;
import com.biteandbyte.restaurant.model.PaymentStatus;
import com.biteandbyte.restaurant.model.User;
import com.biteandbyte.restaurant.repository.AddressRepository;
import com.biteandbyte.restaurant.repository.CartItemRepository;
import com.biteandbyte.restaurant.repository.CustomerOrderRepository;
import com.biteandbyte.restaurant.repository.PaymentRepository;
import com.biteandbyte.restaurant.repository.UserRepository;
import com.biteandbyte.restaurant.security.CurrentUser;
import com.biteandbyte.restaurant.service.OrderStatusPolicy.Actor;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout and the customer's own orders. Every lookup is scoped to {@link CurrentUser}, so
 * another customer's order answers 404 (gap G23).
 */
@Service
public class OrderService {

    private final CustomerOrderRepository orders;
    private final CartItemRepository cartItems;
    private final AddressRepository addresses;
    private final UserRepository users;
    private final PaymentRepository payments;
    private final PricingCalculator pricing;
    private final OrderNumberGenerator orderNumbers;
    private final OrderLifecycle lifecycle;
    private final CurrentUser currentUser;
    private final Clock clock;

    public OrderService(CustomerOrderRepository orders, CartItemRepository cartItems, AddressRepository addresses,
                        UserRepository users, PaymentRepository payments, PricingCalculator pricing,
                        OrderNumberGenerator orderNumbers, OrderLifecycle lifecycle, CurrentUser currentUser,
                        Clock clock) {
        this.orders = orders;
        this.cartItems = cartItems;
        this.addresses = addresses;
        this.users = users;
        this.payments = payments;
        this.pricing = pricing;
        this.orderNumbers = orderNumbers;
        this.lifecycle = lifecycle;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Turns the cart into an order in one transaction: check every line, price it from the
     * database, copy the address, take the stock, save the order, empty the cart.
     *
     * <p>Two customers buying the last unit at the same time both pass the stock check, but
     * {@code @Version} on the menu item lets only one stock update commit. The other gets 409
     * {@code CONCURRENT_UPDATE} and nothing of their order is saved.
     */
    @Transactional
    public OrderResponse place(PlaceOrderRequest request) {
        Long userId = currentUser.id();
        List<CartItem> lines = cartItems.findByUserIdOrderByAddedAtAsc(userId);
        if (lines.isEmpty()) {
            throw ApiException.badRequest("CART_EMPTY", "Your cart is empty");
        }
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Address address = resolveAddress(userId, request.addressId());

        CustomerOrder order = new CustomerOrder();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem line : lines) {
            MenuItem item = line.getMenuItem();
            StockCheck.require(item, line.getQuantity());
            item.setStockQuantity(item.getStockQuantity() - line.getQuantity());
            OrderItem orderItem = OrderItem.of(item, line.getQuantity());
            order.addItem(orderItem);
            subtotal = subtotal.add(orderItem.getLineTotal());
        }

        PricingCalculator.Totals totals = pricing.calculate(subtotal);
        order.setUser(user);
        order.setOrderNumber(orderNumbers.next());
        order.setPaymentMethod(request.paymentMethod());
        // Cash on delivery needs no payment step. Online orders wait for payment (Phase 4).
        order.setStatus(request.paymentMethod() == PaymentMethod.COD ? OrderStatus.CONFIRMED : OrderStatus.PENDING);
        order.setSubtotal(totals.subtotal());
        order.setTaxAmount(totals.taxAmount());
        order.setDeliveryCharge(totals.deliveryCharge());
        order.setGrandTotal(totals.grandTotal());
        order.snapshotAddress(address, user.getFullName(), user.getPhone());
        order.setOrderedAt(clock.instant());
        orders.save(order);

        if (request.paymentMethod() == PaymentMethod.COD) {
            Payment payment = new Payment();
            payment.setOrder(order);
            payment.setMethod(PaymentMethod.COD);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setAmount(totals.grandTotal());
            payments.save(payment);
        }

        cartItems.deleteAll(lines);
        // Write the stock updates now, so a lost race fails here and rolls everything back.
        orders.flush();
        return lifecycle.toResponse(order, Actor.CUSTOMER);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> list(OrderStatus status, Pageable pageable) {
        Long userId = currentUser.id();
        Specification<CustomerOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(orders.findAll(spec, pageable), OrderSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return lifecycle.toResponse(findOwn(id), Actor.CUSTOMER);
    }

    /** Customers may cancel while the order is PENDING or CONFIRMED. Stock goes back to the menu. */
    @Transactional
    public OrderResponse cancel(Long id, String reason) {
        CustomerOrder order = findOwn(id);
        lifecycle.transition(order, OrderStatus.CANCELLED, Actor.CUSTOMER, reason);
        orders.flush();
        return lifecycle.toResponse(order, Actor.CUSTOMER);
    }

    private CustomerOrder findOwn(Long id) {
        return orders.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private Address resolveAddress(Long userId, Long addressId) {
        if (addressId != null) {
            return addresses.findByIdAndUserId(addressId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
        }
        return addresses.findFirstByUserIdAndDefaultAddressTrue(userId)
                .orElseThrow(() -> ApiException.badRequest("ADDRESS_REQUIRED",
                        "Add a delivery address before placing an order"));
    }
}
