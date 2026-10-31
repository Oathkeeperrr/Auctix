package com.auctix.dto.common;

import com.auctix.domain.Category;

public record CategoryDto(String name, String label, String modelKind) {
    public static CategoryDto from(Category category) {
        return new CategoryDto(category.name(), category.getLabel(), category.getDefaultModel().name());
    }
}
