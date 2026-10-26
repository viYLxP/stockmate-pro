package com.stockmate.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(@NotBlank @Size(max = 100) String name, @Size(max = 255) String description) {
    }

    // productCount lets the UI show the number and disable delete when it is not zero.
    // productCount يسمح للواجهة تعرض العدد وتعطّل زر الحذف لو ما كان صفر.
    public record CategoryResponse(Long id, String name, String description, long productCount) {

        public static CategoryResponse from(Category category, long productCount) {
            return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), productCount);
        }
    }
}
