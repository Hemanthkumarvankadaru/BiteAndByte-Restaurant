package com.biteandbyte.restaurant.controller;

import com.biteandbyte.restaurant.dto.ApiResponse;
import com.biteandbyte.restaurant.dto.PageResponse;
import com.biteandbyte.restaurant.dto.menu.CategoryResponse;
import com.biteandbyte.restaurant.dto.menu.MenuItemResponse;
import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.service.CategoryService;
import com.biteandbyte.restaurant.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only menu. No login needed. */
@Tag(name = "Menu", description = "Public menu and categories")
@SecurityRequirements
@RestController
public class MenuController {

    static final Set<String> SORTABLE = Set.of("name", "price", "createdAt");
    static final Sort DEFAULT_SORT = Sort.by("category.displayOrder", "name", "id");

    private final MenuService menuService;
    private final CategoryService categoryService;

    public MenuController(MenuService menuService, CategoryService categoryService) {
        this.menuService = menuService;
        this.categoryService = categoryService;
    }

    @Operation(summary = "Search the menu",
            description = "Lists dishes in active categories. Dishes that cannot be ordered right now are included "
                    + "with orderable=false unless availableOnly=true. Default order: category, then name.")
    @GetMapping("/api/menu")
    public ApiResponse<PageResponse<MenuItemResponse>> search(
            @Parameter(description = "Text in the name or description", example = "biryani")
            @RequestParam(required = false) String q,
            @Parameter(description = "Category id", example = "3") @RequestParam(required = false) Long category,
            @Parameter(description = "true for veg only, false for non-veg only") @RequestParam(required = false) Boolean veg,
            @Parameter(description = "Only dishes that can be ordered now") @RequestParam(defaultValue = "false") boolean availableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = Paging.DEFAULT_SIZE) int size,
            @Parameter(description = "name, price or createdAt, then ,asc or ,desc", example = "price,asc")
            @RequestParam(required = false) String sort) {
        checkQuery(q);
        return ApiResponse.ok(menuService.search(new MenuService.MenuFilter(q, category, veg, availableOnly, false),
                Paging.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @Operation(summary = "One dish")
    @GetMapping("/api/menu/{id}")
    public ApiResponse<MenuItemResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(menuService.get(id));
    }

    @Operation(summary = "Active categories in menu order")
    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryResponse>> categories() {
        return ApiResponse.ok(categoryService.listActive());
    }

    @Operation(summary = "One active category")
    @GetMapping("/api/categories/{id}")
    public ApiResponse<CategoryResponse> category(@PathVariable Long id) {
        return ApiResponse.ok(categoryService.getActive(id));
    }

    static void checkQuery(String q) {
        if (q != null && q.length() > MenuService.MAX_QUERY_LENGTH) {
            throw ApiException.badRequest("INVALID_PARAMETER",
                    "Parameter 'q' must be at most " + MenuService.MAX_QUERY_LENGTH + " characters");
        }
    }
}
