package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.dto.menu.CategoryRequest;
import com.biteandbyte.restaurant.dto.menu.CategoryResponse;
import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.exception.ResourceNotFoundException;
import com.biteandbyte.restaurant.model.Category;
import com.biteandbyte.restaurant.repository.CategoryItemCount;
import com.biteandbyte.restaurant.repository.CategoryRepository;
import com.biteandbyte.restaurant.repository.MenuItemRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Menu categories. Customers see active ones only; the admin manages all of them. */
@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final MenuItemRepository menuItems;

    public CategoryService(CategoryRepository categories, MenuItemRepository menuItems) {
        this.categories = categories;
        this.menuItems = menuItems;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActive() {
        return withCounts(categories.findByActiveTrueOrderByDisplayOrderAscNameAsc());
    }

    @Transactional(readOnly = true)
    public CategoryResponse getActive(Long id) {
        Category category = categories.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        return CategoryResponse.from(category, menuItems.countByCategoryIdAndDeletedFalse(id));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return withCounts(categories.findAllByOrderByDisplayOrderAscNameAsc());
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw nameTaken();
        }
        Category category = new Category();
        apply(category, request);
        return CategoryResponse.from(categories.saveAndFlush(category), 0);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = find(id);
        if (categories.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw nameTaken();
        }
        apply(category, request);
        categories.saveAndFlush(category);
        return CategoryResponse.from(category, menuItems.countByCategoryIdAndDeletedFalse(id));
    }

    /**
     * Deletes an empty category. Blocked while it holds dishes. A category whose dishes were all
     * soft-deleted still cannot be removed, because past orders reference those dishes; it can be
     * deactivated instead.
     */
    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        long live = menuItems.countByCategoryIdAndDeletedFalse(id);
        if (live > 0) {
            throw ApiException.conflict("CATEGORY_NOT_EMPTY",
                    "This category still has " + live + " dish(es). Move or delete them first.");
        }
        if (menuItems.existsByCategoryId(id)) {
            throw ApiException.conflict("CATEGORY_HAS_HISTORY",
                    "Deleted dishes in this category are kept for order history. Deactivate the category instead.");
        }
        categories.delete(category);
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(blankToNull(request.description()));
        category.setImageUrl(blankToNull(request.imageUrl()));
        category.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        category.setActive(request.active() == null || request.active());
    }

    private List<CategoryResponse> withCounts(List<Category> list) {
        Map<Long, Long> counts = menuItems.countLiveItemsByCategory().stream()
                .collect(Collectors.toMap(CategoryItemCount::categoryId, CategoryItemCount::count));
        return list.stream()
                .map(c -> CategoryResponse.from(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    private Category find(Long id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private static ApiException nameTaken() {
        return ApiException.conflict("CATEGORY_NAME_TAKEN", "A category with this name already exists");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
