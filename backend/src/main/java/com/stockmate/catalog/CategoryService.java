package com.stockmate.catalog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockmate.catalog.CategoryDtos.CategoryRequest;
import com.stockmate.catalog.CategoryDtos.CategoryResponse;
import com.stockmate.common.ApiException;
import com.stockmate.common.ErrorCode;
import com.stockmate.product.ProductRepository;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAllWithProductCount().stream()
                .map(row -> CategoryResponse.from(row.getCategory(), row.getProductCount()))
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        ensureNameIsFree(name, null);
        Category category = new Category(name, blankToNull(request.description()));
        category.setNameAr(blankToNull(request.nameAr()));
        Category saved = categories.save(category);
        return CategoryResponse.from(saved, 0);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = get(id);
        String name = request.name().trim();
        ensureNameIsFree(name, id);
        category.setName(name);
        category.setNameAr(blankToNull(request.nameAr()));
        category.setDescription(blankToNull(request.description()));
        return CategoryResponse.from(category, products.countByCategoryId(id));
    }

    @Transactional
    public void delete(Long id) {
        Category category = get(id);
        // Refuse to delete a category that still has products, otherwise those products would be orphaned.
        // نرفض حذف تصنيف فيه منتجات، وإلا تصير المنتجات بدون تصنيف.
        long count = products.countByCategoryId(id);
        if (count > 0) {
            throw new ApiException(ErrorCode.IN_USE, java.util.Map.of("productCount", count));
        }
        categories.delete(category);
    }

    public Category get(Long id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("category", id));
    }

    /**
     * Fails if another category (other than {@code currentId}) already uses this name.
     * يرمي خطأ لو فيه تصنيف ثاني (غير {@code currentId}) يستخدم نفس الاسم.
     */
    private void ensureNameIsFree(String name, Long currentId) {
        categories.findByNameIgnoreCase(name)
                // Renaming a category to its own current name is allowed.
                // مسموح نحفظ التصنيف بنفس اسمه الحالي.
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.NAME_EXISTS);
                });
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
