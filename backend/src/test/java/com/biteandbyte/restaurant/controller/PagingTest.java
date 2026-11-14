package com.biteandbyte.restaurant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biteandbyte.restaurant.exception.ApiException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PagingTest {

    private static final Set<String> SORTABLE = Set.of("name", "price");
    private static final Sort DEFAULT = Sort.by("name");

    @Test
    void buildsPageAndSort() {
        Pageable pageable = Paging.of(2, 20, "price,desc", SORTABLE, DEFAULT);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "price").and(Sort.by("id")));
        assertThat(Paging.of(0, 12, null, SORTABLE, DEFAULT).getSort()).isEqualTo(DEFAULT);
        assertThat(Paging.of(0, 12, "name", SORTABLE, DEFAULT).getSort().getOrderFor("name").isAscending()).isTrue();
    }

    @Test
    void rejectsBadValues() {
        assertInvalid(() -> Paging.of(-1, 12, null, SORTABLE, DEFAULT));
        assertInvalid(() -> Paging.of(0, 0, null, SORTABLE, DEFAULT));
        assertInvalid(() -> Paging.of(0, 101, null, SORTABLE, DEFAULT));
        assertInvalid(() -> Paging.of(0, 12, "passwordHash", SORTABLE, DEFAULT));
        assertInvalid(() -> Paging.of(0, 12, "price,sideways", SORTABLE, DEFAULT));
        assertInvalid(() -> Paging.of(0, 12, "price,asc,extra", SORTABLE, DEFAULT));
    }

    private static void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_PARAMETER"));
    }
}
