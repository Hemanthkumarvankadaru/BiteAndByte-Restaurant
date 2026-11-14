package com.biteandbyte.restaurant.repository;

import com.biteandbyte.restaurant.model.CustomerOrder;
import com.biteandbyte.restaurant.model.OrderStatus;
import com.biteandbyte.restaurant.model.PaymentMethod;
import com.biteandbyte.restaurant.model.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long>, JpaSpecificationExecutor<CustomerOrder> {

    Optional<CustomerOrder> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    /** Ownership-safe lookup: returns empty when the order belongs to someone else. */
    Optional<CustomerOrder> findByIdAndUserId(Long id, Long userId);

    long countByOrderedAtGreaterThanEqualAndOrderedAtLessThan(Instant from, Instant to);

    @Query("select new com.biteandbyte.restaurant.repository.StatusCount(o.status, count(o)) "
            + "from CustomerOrder o group by o.status")
    List<StatusCount> countByStatusGrouped();

    /**
     * Orders that count as revenue (gap G28), delivered in {@code [from, to)}: DELIVERED, and
     * either cash on delivery or backed by a successful online payment.
     */
    default List<RevenueRow> findRevenueRows(Instant from, Instant to) {
        return findRevenueRows(from, to, OrderStatus.DELIVERED, PaymentMethod.COD, PaymentStatus.SUCCESS);
    }

    @Query("""
            select new com.biteandbyte.restaurant.repository.RevenueRow(o.deliveredAt, o.grandTotal)
            from CustomerOrder o
            where o.status = :delivered
              and o.deliveredAt >= :from and o.deliveredAt < :to
              and (o.paymentMethod = :cod
                   or exists (select p.id from Payment p where p.order = o and p.status = :success))
            """)
    List<RevenueRow> findRevenueRows(Instant from, Instant to, OrderStatus delivered, PaymentMethod cod,
                                     PaymentStatus success);
}
