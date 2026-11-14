package com.biteandbyte.restaurant.repository;

import com.biteandbyte.restaurant.model.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = {"menuItem", "menuItem.category"})
    List<CartItem> findByUserIdOrderByAddedAtAsc(Long userId);

    @EntityGraph(attributePaths = {"menuItem", "menuItem.category"})
    Optional<CartItem> findByUserIdAndMenuItemId(Long userId, Long menuItemId);

    @Modifying
    @Query("delete from CartItem c where c.user.id = :userId")
    int deleteAllForUser(Long userId);

    /** Removes a menu item from every cart, used when the item is deleted. */
    @Modifying
    @Query("delete from CartItem c where c.menuItem.id = :menuItemId")
    int deleteAllForMenuItem(Long menuItemId);
}
