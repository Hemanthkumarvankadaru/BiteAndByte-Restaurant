package com.biteandbyte.restaurant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One line of an order. Name and price are copied so menu edits never change history. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    /** Builds a line from the current menu item, copying its name and price. */
    public static OrderItem of(MenuItem menuItem, int quantity) {
        OrderItem line = new OrderItem();
        line.setMenuItem(menuItem);
        line.setItemName(menuItem.getName());
        line.setQuantity(quantity);
        line.setUnitPrice(menuItem.getPrice());
        line.setLineTotal(menuItem.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return line;
    }
}
