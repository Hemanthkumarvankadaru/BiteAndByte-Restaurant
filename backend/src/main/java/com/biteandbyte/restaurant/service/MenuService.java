package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.dto.PageResponse;
import com.biteandbyte.restaurant.dto.menu.AdminMenuItemResponse;
import com.biteandbyte.restaurant.dto.menu.MenuItemRequest;
import com.biteandbyte.restaurant.dto.menu.MenuItemResponse;
import com.biteandbyte.restaurant.dto.menu.StockUpdateRequest;
import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.exception.ResourceNotFoundException;
import com.biteandbyte.restaurant.model.Category;
import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.repository.CartItemRepository;
import com.biteandbyte.restaurant.repository.CategoryRepository;
import com.biteandbyte.restaurant.repository.MenuItemRepository;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public menu reads and admin menu-item management. Items are soft-deleted, never removed. */
@Service
public class MenuService {

    /** Longest accepted search text. */
    public static final int MAX_QUERY_LENGTH = 100;

    /**
     * Menu search filters. Null or false means "no filter".
     *
     * @param availableOnly only dishes that can be ordered right now
     * @param includeDeleted admin only: also return soft-deleted dishes
     */
    public record MenuFilter(String q, Long categoryId, Boolean veg, boolean availableOnly, boolean includeDeleted) {
    }

    private final MenuItemRepository menuItems;
    private final CategoryRepository categories;
    private final CartItemRepository cartItems;

    public MenuService(MenuItemRepository menuItems, CategoryRepository categories, CartItemRepository cartItems) {
        this.menuItems = menuItems;
        this.categories = categories;
        this.cartItems = cartItems;
    }

    // ---- Public ----

    /** Live dishes in active categories. Unavailable dishes are listed with {@code orderable=false}. */
    @Transactional(readOnly = true)
    public PageResponse<MenuItemResponse> search(MenuFilter filter, Pageable pageable) {
        MenuFilter publicFilter = new MenuFilter(filter.q(), filter.categoryId(), filter.veg(), filter.availableOnly(), false);
        return PageResponse.from(menuItems.findAll(specification(publicFilter, true), pageable), MenuItemResponse::from);
    }

    @Transactional(readOnly = true)
    public MenuItemResponse get(Long id) {
        MenuItem item = menuItems.findByIdAndDeletedFalse(id)
                .filter(i -> i.getCategory().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Menu item", id));
        return MenuItemResponse.from(item);
    }

    // ---- Admin ----

    @Transactional(readOnly = true)
    public PageResponse<AdminMenuItemResponse> adminSearch(MenuFilter filter, Pageable pageable) {
        return PageResponse.from(menuItems.findAll(specification(filter, false), pageable), AdminMenuItemResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminMenuItemResponse adminGet(Long id) {
        return AdminMenuItemResponse.from(findAny(id));
    }

    @Transactional
    public AdminMenuItemResponse create(MenuItemRequest request) {
        MenuItem item = new MenuItem();
        apply(item, request);
        return AdminMenuItemResponse.from(menuItems.saveAndFlush(item));
    }

    @Transactional
    public AdminMenuItemResponse update(Long id, MenuItemRequest request) {
        MenuItem item = findLive(id);
        checkVersion(item, request.version());
        apply(item, request);
        return AdminMenuItemResponse.from(menuItems.saveAndFlush(item));
    }

    @Transactional
    public AdminMenuItemResponse updateStock(Long id, StockUpdateRequest request) {
        MenuItem item = findLive(id);
        checkVersion(item, request.version());
        item.setStockQuantity(request.stockQuantity());
        return AdminMenuItemResponse.from(menuItems.saveAndFlush(item));
    }

    /** Soft delete. Past orders keep pointing at the row. The dish is taken out of every cart. */
    @Transactional
    public void delete(Long id) {
        MenuItem item = findLive(id);
        item.setDeleted(true);
        cartItems.deleteAllForMenuItem(id);
    }

    // ---- Helpers ----

    private void apply(MenuItem item, MenuItemRequest request) {
        Category category = categories.findById(request.categoryId())
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_CATEGORY",
                        "Category " + request.categoryId() + " does not exist"));
        item.setCategory(category);
        item.setName(request.name().trim());
        item.setDescription(blankToNull(request.description()));
        item.setPrice(request.price().setScale(2));
        item.setImageUrl(blankToNull(request.imageUrl()));
        item.setStockQuantity(request.stockQuantity());
        item.setAvailable(request.available() == null || request.available());
        item.setVeg(request.veg());
    }

    private MenuItem findLive(Long id) {
        return menuItems.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Menu item", id));
    }

    private MenuItem findAny(Long id) {
        return menuItems.findById(id).orElseThrow(() -> new ResourceNotFoundException("Menu item", id));
    }

    private static void checkVersion(MenuItem item, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != item.getVersion()) {
            throw ApiException.conflict("CONCURRENT_UPDATE",
                    "This item was changed by someone else since you loaded it. Reload and try again.");
        }
    }

    static Specification<MenuItem> specification(MenuFilter filter, boolean publicView) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Path<Category> category = root.get("category");
            if (publicView || !filter.includeDeleted()) {
                predicates.add(cb.isFalse(root.get("deleted")));
            }
            if (publicView) {
                predicates.add(cb.isTrue(category.get("active")));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(category.get("id"), filter.categoryId()));
            }
            if (filter.veg() != null) {
                predicates.add(cb.equal(root.get("veg"), filter.veg()));
            }
            if (filter.availableOnly()) {
                predicates.add(cb.isFalse(root.get("deleted")));
                predicates.add(cb.isTrue(root.get("available")));
                predicates.add(cb.greaterThan(root.get("stockQuantity"), 0));
                predicates.add(cb.isTrue(category.get("active")));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String pattern = likePattern(filter.q());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Case-insensitive "contains" pattern with LIKE wildcards in the input escaped. */
    static String likePattern(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
