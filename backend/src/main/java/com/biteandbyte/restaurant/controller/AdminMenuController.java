package com.biteandbyte.restaurant.controller;

import com.biteandbyte.restaurant.dto.ApiResponse;
import com.biteandbyte.restaurant.dto.PageResponse;
import com.biteandbyte.restaurant.dto.menu.AdminMenuItemResponse;
import com.biteandbyte.restaurant.dto.menu.CategoryRequest;
import com.biteandbyte.restaurant.dto.menu.CategoryResponse;
import com.biteandbyte.restaurant.dto.menu.MenuItemRequest;
import com.biteandbyte.restaurant.dto.menu.StockUpdateRequest;
import com.biteandbyte.restaurant.service.CategoryService;
import com.biteandbyte.restaurant.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Menu and category management. Admin only, enforced in {@code SecurityConfig}. */
@Tag(name = "Admin: menu", description = "Dishes, stock and categories")
@RestController
@RequestMapping("/api/admin")
public class AdminMenuController {

    static final Set<String> SORTABLE = Set.of("name", "price", "stockQuantity", "createdAt", "updatedAt");

    private final MenuService menuService;
    private final CategoryService categoryService;

    public AdminMenuController(MenuService menuService, CategoryService categoryService) {
        this.menuService = menuService;
        this.categoryService = categoryService;
    }

    // ---- Menu items ----

    @Operation(summary = "Search all dishes, including unavailable ones and inactive categories")
    @GetMapping("/menu-items")
    public ApiResponse<PageResponse<AdminMenuItemResponse>> listItems(
            @RequestParam(required = false) String q,
            @Parameter(description = "Category id") @RequestParam(required = false) Long category,
            @RequestParam(required = false) Boolean veg,
            @RequestParam(defaultValue = "false") boolean availableOnly,
            @Parameter(description = "Also list soft-deleted dishes") @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = Paging.DEFAULT_SIZE) int size,
            @Parameter(description = "name, price, stockQuantity, createdAt or updatedAt, then ,asc or ,desc",
                    example = "stockQuantity,asc")
            @RequestParam(required = false) String sort) {
        MenuController.checkQuery(q);
        return ApiResponse.ok(menuService.adminSearch(
                new MenuService.MenuFilter(q, category, veg, availableOnly, includeDeleted),
                Paging.of(page, size, sort, SORTABLE, MenuController.DEFAULT_SORT)));
    }

    @Operation(summary = "One dish, including soft-deleted ones")
    @GetMapping("/menu-items/{id}")
    public ApiResponse<AdminMenuItemResponse> getItem(@PathVariable Long id) {
        return ApiResponse.ok(menuService.adminGet(id));
    }

    @Operation(summary = "Add a dish")
    @PostMapping("/menu-items")
    public ResponseEntity<ApiResponse<AdminMenuItemResponse>> createItem(@Valid @RequestBody MenuItemRequest request) {
        AdminMenuItemResponse item = menuService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/menu-items/" + item.id()))
                .body(ApiResponse.ok(item, "Dish added"));
    }

    @Operation(summary = "Replace a dish",
            description = "Send the version you loaded to get 409 CONCURRENT_UPDATE instead of overwriting someone else's change.")
    @PutMapping("/menu-items/{id}")
    public ApiResponse<AdminMenuItemResponse> updateItem(@PathVariable Long id, @Valid @RequestBody MenuItemRequest request) {
        return ApiResponse.ok(menuService.update(id, request), "Dish updated");
    }

    @Operation(summary = "Set stock",
            description = "Zero stock makes the dish unorderable. Restocking makes it orderable again unless it is switched off.")
    @PatchMapping("/menu-items/{id}/stock")
    public ApiResponse<AdminMenuItemResponse> updateStock(@PathVariable Long id, @Valid @RequestBody StockUpdateRequest request) {
        return ApiResponse.ok(menuService.updateStock(id, request), "Stock updated");
    }

    @Operation(summary = "Delete a dish",
            description = "Soft delete: hidden from the menu and removed from carts, but kept for order history.")
    @DeleteMapping("/menu-items/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        menuService.delete(id);
        return ApiResponse.message("Dish deleted");
    }

    // ---- Categories ----

    @Operation(summary = "All categories, including inactive ones")
    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> listCategories() {
        return ApiResponse.ok(categoryService.listAll());
    }

    @Operation(summary = "Add a category")
    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse category = categoryService.create(request);
        return ResponseEntity.created(URI.create("/api/categories/" + category.id()))
                .body(ApiResponse.ok(category, "Category added"));
    }

    @Operation(summary = "Replace a category")
    @PutMapping("/categories/{id}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryService.update(id, request), "Category updated");
    }

    @Operation(summary = "Delete an empty category",
            description = "409 CATEGORY_NOT_EMPTY while it has dishes. 409 CATEGORY_HAS_HISTORY when only deleted "
                    + "dishes remain, because orders reference them: deactivate it instead.")
    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.message("Category deleted");
    }
}
