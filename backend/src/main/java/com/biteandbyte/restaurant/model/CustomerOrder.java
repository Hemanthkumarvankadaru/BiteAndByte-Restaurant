package com.biteandbyte.restaurant.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A placed order, stored in table {@code orders}. Named CustomerOrder because ORDER is a
 * keyword in SQL and JPQL.
 *
 * <p>The delivery address is copied onto the order at checkout so later edits to the
 * customer's saved address never change order history. {@code address} is only a reference.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class CustomerOrder extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private Address address;

    @Column(name = "order_number", nullable = false, length = 30)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "delivery_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryCharge;

    @Column(name = "grand_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "delivery_name", nullable = false, length = 100)
    private String deliveryName;

    @Column(name = "delivery_phone", nullable = false, length = 15)
    private String deliveryPhone;

    @Column(name = "delivery_street", nullable = false, length = 200)
    private String deliveryStreet;

    @Column(name = "delivery_city", nullable = false, length = 60)
    private String deliveryCity;

    @Column(name = "delivery_state", nullable = false, length = 60)
    private String deliveryState;

    @Column(name = "delivery_pincode", nullable = false, length = 6)
    private String deliveryPincode;

    @Column(name = "delivery_landmark", length = 100)
    private String deliveryLandmark;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    /** Adds a line and keeps both sides of the relationship in sync. */
    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    /** Copies a saved address onto the order. */
    public void snapshotAddress(Address source, String recipientName, String recipientPhone) {
        this.address = source;
        this.deliveryName = recipientName;
        this.deliveryPhone = recipientPhone;
        this.deliveryStreet = source.getStreet();
        this.deliveryCity = source.getCity();
        this.deliveryState = source.getState();
        this.deliveryPincode = source.getPincode();
        this.deliveryLandmark = source.getLandmark();
    }
}
