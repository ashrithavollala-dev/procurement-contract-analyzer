package com.company.contractanalyzer.repository;

import com.company.contractanalyzer.entity.Contract;
import com.company.contractanalyzer.enums.ContractStatus;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Composable, null-safe filters for the contract list endpoint. */
public final class ContractSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ContractSpecifications() {
    }

    public static Specification<Contract> hasStatus(ContractStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    /** Case-insensitive substring match on title, vendor name or original filename. */
    public static Specification<Contract> matchesText(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(cb.coalesce(root.get("vendorName"), "")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(root.get("originalFilename")), pattern, LIKE_ESCAPE));
        };
    }

    /** Escapes LIKE wildcards so user input is matched literally. */
    static String escapeLike(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
