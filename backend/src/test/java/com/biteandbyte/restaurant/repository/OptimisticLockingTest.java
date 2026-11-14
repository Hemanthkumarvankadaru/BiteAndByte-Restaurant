package com.biteandbyte.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biteandbyte.restaurant.model.Category;
import com.biteandbyte.restaurant.model.MenuItem;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Two people editing the same menu item: the second save, based on a stale copy, must fail
 * instead of silently overwriting stock. Not transactional, so each save commits on its own.
 */
@SpringBootTest
class OptimisticLockingTest {

    @Autowired private MenuItemRepository menuItems;
    @Autowired private CategoryRepository categories;

    private Long itemId;

    @AfterEach
    void cleanUp() {
        if (itemId != null) {
            menuItems.deleteById(itemId);
        }
    }

    @Test
    void staleStockUpdateIsRejected() {
        Category starters = categories.findByNameIgnoreCase("Starters").orElseThrow();
        MenuItem item = new MenuItem();
        item.setCategory(starters);
        item.setName("Lock Test Item");
        item.setPrice(new BigDecimal("10.00"));
        item.setStockQuantity(1);
        itemId = menuItems.save(item).getId();

        MenuItem firstCopy = menuItems.findById(itemId).orElseThrow();
        MenuItem secondCopy = menuItems.findById(itemId).orElseThrow();

        firstCopy.setStockQuantity(0);
        menuItems.save(firstCopy);

        secondCopy.setStockQuantity(0);
        assertThatThrownBy(() -> menuItems.save(secondCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(menuItems.findById(itemId).orElseThrow().getVersion()).isEqualTo(1);
    }
}
