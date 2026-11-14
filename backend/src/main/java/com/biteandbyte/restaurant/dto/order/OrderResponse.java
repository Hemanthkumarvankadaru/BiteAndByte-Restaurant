package com.biteandbyte.restaurant.dto.order;

import com.biteandbyte.restaurant.model.CustomerOrder;
import com.biteandbyte.restaurant.model.OrderItem;
import com.biteandbyte.restaurant.model.OrderStatus;
import com.biteandbyte.restaurant.model.Payment;
import com.biteandbyte.restaurant.model.PaymentMethod;
import com.biteandbyte.restaurant.model.PaymentStatus;
import com.biteandbyte.restaurant.model.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full order detail. Address, item names and prices are the copies taken at checkout.
 *
 * @param allowedNextStatuses statuses the caller may move this order to. For a customer this is
 *                            [CANCELLED] while cancelling is still allowed, otherwise empty.
 * @param customer            who placed it. Only filled in for the admin.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        @Schema(example = "41") Long id,
        @Schema(example = "BB-20261114-7KQ2M") String orderNumber,
        @Schema(example = "CONFIRMED") OrderStatus status,
        @Schema(example = "COD") PaymentMethod paymentMethod,
        @Schema(example = "598.00") BigDecimal subtotal,
        @Schema(example = "29.90") BigDecimal taxAmount,
        @Schema(example = "0.00") BigDecimal deliveryCharge,
        @Schema(example = "627.90") BigDecimal grandTotal,
        DeliveryAddress deliveryAddress,
        List<Item> items,
        List<PaymentInfo> payments,
        List<OrderStatus> allowedNextStatuses,
        String cancelReason,
        Instant orderedAt,
        Instant deliveredAt,
        Instant cancelledAt,
        Customer customer) {

    public record DeliveryAddress(
            @Schema(example = "Priya Sharma") String name,
            @Schema(example = "9876543210") String phone,
            @Schema(example = "8-2-293 Road No. 12, Banjara Hills") String street,
            @Schema(example = "Hyderabad") String city,
            @Schema(example = "Telangana") String state,
            @Schema(example = "500034") String pincode,
            String landmark) {
    }

    public record Item(
            @Schema(example = "7") Long menuItemId,
            @Schema(example = "Hyderabadi Chicken Dum Biryani") String itemName,
            @Schema(example = "2") int quantity,
            @Schema(example = "299.00") BigDecimal unitPrice,
            @Schema(example = "598.00") BigDecimal lineTotal) {

        static Item from(OrderItem line) {
            return new Item(line.getMenuItem().getId(), line.getItemName(), line.getQuantity(),
                    line.getUnitPrice(), line.getLineTotal());
        }
    }

    public record PaymentInfo(
            @Schema(example = "55") Long id,
            @Schema(example = "COD") PaymentMethod method,
            @Schema(example = "PENDING") PaymentStatus status,
            @Schema(example = "627.90") BigDecimal amount,
            Instant paidAt) {

        static PaymentInfo from(Payment payment) {
            return new PaymentInfo(payment.getId(), payment.getMethod(), payment.getStatus(),
                    payment.getAmount(), payment.getPaidAt());
        }
    }

    public record Customer(Long id, String username, String email, String phone) {

        static Customer from(User user) {
            return new Customer(user.getId(), user.getUsername(), user.getEmail(), user.getPhone());
        }
    }

    /**
     * @param includeCustomer true for the admin view
     */
    public static OrderResponse from(CustomerOrder order, List<Payment> payments, List<OrderStatus> allowedNext,
                                     boolean includeCustomer) {
        DeliveryAddress address = new DeliveryAddress(order.getDeliveryName(), order.getDeliveryPhone(),
                order.getDeliveryStreet(), order.getDeliveryCity(), order.getDeliveryState(),
                order.getDeliveryPincode(), order.getDeliveryLandmark());
        return new OrderResponse(order.getId(), order.getOrderNumber(), order.getStatus(), order.getPaymentMethod(),
                order.getSubtotal(), order.getTaxAmount(), order.getDeliveryCharge(), order.getGrandTotal(),
                address,
                order.getItems().stream().map(Item::from).toList(),
                payments.stream().map(PaymentInfo::from).toList(),
                allowedNext,
                order.getCancelReason(), order.getOrderedAt(), order.getDeliveredAt(), order.getCancelledAt(),
                includeCustomer ? Customer.from(order.getUser()) : null);
    }
}
