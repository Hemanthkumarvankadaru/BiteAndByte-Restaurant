package com.biteandbyte.restaurant.repository;

import com.biteandbyte.restaurant.model.MenuItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Soft-deleted items are excluded by every finder except the inherited ones. */
public interface MenuItemRepository extends JpaRepository<MenuItem, Long>, JpaSpecificationExecutor<MenuItem> {

    /** Menu searches. Loads the category in the same query, because every response shows it. */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<MenuItem> findAll(Specification<MenuItem> spec, Pageable pageable);

    Optional<MenuItem> findByIdAndDeletedFalse(Long id);

    List<MenuItem> findByCategoryIdAndDeletedFalseOrderByNameAsc(Long categoryId);

    long countByCategoryIdAndDeletedFalse(Long categoryId);

    /** True when any item, including soft-deleted ones, still points at the category. */
    boolean existsByCategoryId(Long categoryId);

    @EntityGraph(attributePaths = "category")
    List<MenuItem> findByDeletedFalseAndStockQuantityLessThanEqualOrderByStockQuantityAsc(int threshold);

    /** Live (not deleted) item count per category id. */
    @Query("select new com.biteandbyte.restaurant.repository.CategoryItemCount(m.category.id, count(m)) "
            + "from MenuItem m where m.deleted = false group by m.category.id")
    List<CategoryItemCount> countLiveItemsByCategory();
}
