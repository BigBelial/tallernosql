package com.noSQL.claseJava.service;

import com.noSQL.claseJava.config.CacheNames;
import com.noSQL.claseJava.dto.CategoryRequest;
import com.noSQL.claseJava.entity.Category;
import com.noSQL.claseJava.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
    public Category createCategory(CategoryRequest request) {
        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return categoryRepository.save(category);
    }

    @Cacheable(CacheNames.CATEGORIES)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Cacheable(cacheNames = CacheNames.CATEGORIES, unless = "#result == null")
    public Optional<Category> getCategoryById(String id) {
        return categoryRepository.findById(id);
    }

    @Cacheable(cacheNames = CacheNames.CATEGORIES, unless = "#result == null")
    public Optional<Category> getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }

    // Se devuelve un ArrayList (y no List.of()) porque el resultado se guarda en Redis
    @Cacheable(CacheNames.CATEGORIES)
    public List<Category.BookReference> getCategoryBooks(String categoryId) {
        Optional<Category> category = categoryRepository.findById(categoryId);
        if (category.isPresent() && category.get().getBooks() != null) {
            return category.get().getBooks();
        }
        return new ArrayList<>();
    }

    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
    public Category updateCategory(String id, CategoryRequest request) {
        Optional<Category> existingCategory = categoryRepository.findById(id);
        if (existingCategory.isPresent()) {
            Category category = existingCategory.get();
            category.setName(request.getName());
            category.setDescription(request.getDescription());
            return categoryRepository.save(category);
        }
        return null;
    }

    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
    public boolean deleteCategory(String id) {
        if (categoryRepository.existsById(id)) {
            categoryRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
