package com.barberia.api.shared.pagination;

import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class CatalogPage {
    private CatalogPage() { }
    public static Pageable of(int page, int size, String sort, Map<String, String> allowed) {
        if (page < 0 || size < 1 || size > 100 || sort == null) { throw new IllegalArgumentException(); }
        String[] parts = sort.split(",", -1);
        if (parts.length > 2 || !allowed.containsKey(parts[0])) { throw new IllegalArgumentException(); }
        String direction = parts.length == 2 ? parts[1] : "asc";
        if (!direction.equals("asc") && !direction.equals("desc")) { throw new IllegalArgumentException(); }
        String field = allowed.get(parts[0]);
        Sort order = Sort.by(Sort.Direction.fromString(direction), field);
        if (!field.equals("id")) { order = order.and(Sort.by("id")); }
        return PageRequest.of(page, size, order);
    }
}
