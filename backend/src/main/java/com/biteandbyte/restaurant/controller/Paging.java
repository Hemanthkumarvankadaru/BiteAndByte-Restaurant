package com.biteandbyte.restaurant.controller;

import com.biteandbyte.restaurant.exception.ApiException;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds a {@link Pageable} from the list parameters in the API contract: {@code page} (from 0),
 * {@code size} (default 12, at most 100) and {@code sort=field,asc|desc}. Only whitelisted fields
 * can be sorted on, so clients cannot sort by internal or unindexed columns. Bad values answer
 * 400 {@code INVALID_PARAMETER}.
 */
public final class Paging {

    public static final String DEFAULT_SIZE = "12";
    public static final int MAX_SIZE = 100;

    public static Pageable of(int page, int size, String sort, Set<String> sortable, Sort defaultSort) {
        if (page < 0) {
            throw invalid("Parameter 'page' must be 0 or more");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw invalid("Parameter 'size' must be between 1 and " + MAX_SIZE);
        }
        return PageRequest.of(page, size, parseSort(sort, sortable, defaultSort));
    }

    static Sort parseSort(String sort, Set<String> sortable, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (parts.length > 2 || !sortable.contains(field)) {
            throw invalid("Cannot sort by '" + sort + "'. Use field,asc or field,desc with field one of "
                    + new TreeSet<>(sortable));
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            String dir = parts[1].trim().toLowerCase(Locale.ROOT);
            if (!dir.equals("asc") && !dir.equals("desc")) {
                throw invalid("Sort direction must be asc or desc");
            }
            direction = dir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        }
        // Id as a tie-breaker keeps page boundaries stable when many rows share a value.
        return Sort.by(direction, field).and(Sort.by("id"));
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest("INVALID_PARAMETER", message);
    }

    private Paging() {
    }
}
