package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.dto.cart.CartDtos;
import com.biteandbyte.restaurant.dto.cart.CartItemRequest;
import com.biteandbyte.restaurant.dto.cart.CartQuantityRequest;
import com.biteandbyte.restaurant.dto.cart.CartResponse;
import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.exception.ResourceNotFoundException;
import com.biteandbyte.restaurant.model.CartItem;
import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.repository.CartItemRepository;
import com.biteandbyte.restaurant.repository.MenuItemRepository;
import com.biteandbyte.restaurant.repository.UserRepository;
import com.biteandbyte.restaurant.security.CurrentUser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The logged-in customer's server-side cart. Adding or changing a line checks availability and
 * stock at that moment. Stock is only reserved at checkout, so the cart view re-checks every
 * line and reports problems instead of failing.
 */
@Service
public class CartService {

    private final CartItemRepository cartItems;
    private final MenuItemRepository menuItems;
    private final UserRepository users;
    private final PricingCalculator pricing;
    private final CurrentUser currentUser;

    public CartService(CartItemRepository cartItems, MenuItemRepository menuItems, UserRepository users,
                       PricingCalculator pricing, CurrentUser currentUser) {
        this.cartItems = cartItems;
        this.menuItems = menuItems;
        this.users = users;
        this.pricing = pricing;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public CartResponse view() {
        return render(cartItems.findByUserIdOrderByAddedAtAsc(currentUser.id()));
    }

    /** Adds a dish. If it is already in the cart, the quantities are added together. */
    @Transactional
    public CartResponse add(CartItemRequest request) {
        Long userId = currentUser.id();
        MenuItem item = menuItems.findByIdAndDeletedFalse(request.menuItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu item", request.menuItemId()));
        CartItem line = cartItems.findByUserIdAndMenuItemId(userId, item.getId()).orElse(null);
        int quantity = request.quantity() + (line == null ? 0 : line.getQuantity());
        checkLimit(quantity);
        StockCheck.require(item, quantity);
        if (line == null) {
            line = new CartItem();
            line.setUser(users.getReferenceById(userId));
            line.setMenuItem(item);
        }
        line.setQuantity(quantity);
        cartItems.saveAndFlush(line);
        return render(cartItems.findByUserIdOrderByAddedAtAsc(userId));
    }

    @Transactional
    public CartResponse setQuantity(Long menuItemId, CartQuantityRequest request) {
        Long userId = currentUser.id();
        CartItem line = cartItems.findByUserIdAndMenuItemId(userId, menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", menuItemId));
        checkLimit(request.quantity());
        StockCheck.require(line.getMenuItem(), request.quantity());
        line.setQuantity(request.quantity());
        cartItems.saveAndFlush(line);
        return render(cartItems.findByUserIdOrderByAddedAtAsc(userId));
    }

    /** Removes a dish. Removing one that is not in the cart is not an error. */
    @Transactional
    public CartResponse remove(Long menuItemId) {
        Long userId = currentUser.id();
        cartItems.findByUserIdAndMenuItemId(userId, menuItemId).ifPresent(line -> {
            cartItems.delete(line);
            cartItems.flush();
        });
        return render(cartItems.findByUserIdOrderByAddedAtAsc(userId));
    }

    @Transactional
    public CartResponse clear() {
        cartItems.deleteAllForUser(currentUser.id());
        return render(List.of());
    }

    private CartResponse render(List<CartItem> lines) {
        List<CartResponse.Line> rendered = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int units = 0;
        boolean allOrderable = true;
        for (CartItem line : lines) {
            MenuItem item = line.getMenuItem();
            StockCheck.Problem problem = StockCheck.problem(item, line.getQuantity());
            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
            rendered.add(new CartResponse.Line(item.getId(), item.getName(), item.getImageUrl(), item.isVeg(),
                    item.getPrice(), line.getQuantity(), lineTotal, item.getStockQuantity(),
                    problem == null ? null : problem.name(),
                    problem == null ? null : StockCheck.message(problem, item)));
            subtotal = subtotal.add(lineTotal);
            units += line.getQuantity();
            allOrderable &= problem == null;
        }
        PricingCalculator.Totals totals = pricing.calculate(subtotal);
        return new CartResponse(rendered, units, totals.subtotal(), totals.taxAmount(), totals.deliveryCharge(),
                totals.grandTotal(), !lines.isEmpty() && allOrderable);
    }

    private static void checkLimit(int quantity) {
        if (quantity > CartDtos.MAX_QUANTITY) {
            throw ApiException.badRequest("QUANTITY_LIMIT",
                    "You can order at most " + CartDtos.MAX_QUANTITY + " of one dish");
        }
    }
}
