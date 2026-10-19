package com.noSQL.claseJava.service.impl;

import com.noSQL.claseJava.service.CategoryService;
import com.noSQL.claseJava.service.DenormalizationService;

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
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final DenormalizationService denormalizationService;

    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
    @Override
    public Category createCategory(CategoryRequest request) {
        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return categoryRepository.save(category);
    }

    @Cacheable(CacheNames.CATEGORIES)
    @Override
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Cacheable(cacheNames = CacheNames.CATEGORIES, unless = "#result == null")
    @Override
    public Optional<Category> getCategoryById(String id) {
        return categoryRepository.findById(id);
    }

    @Cacheable(cacheNames = CacheNames.CATEGORIES, unless = "#result == null")
    @Override
    public Optional<Category> getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }

    // Se devuelve un ArrayList (y no List.of()) porque el resultado se guarda en Redis
    @Cacheable(CacheNames.CATEGORIES)
    @Override
    public List<Category.BookReference> getCategoryBooks(String categoryId) {
        Optional<Category> category = categoryRepository.findById(categoryId);
        if (category.isPresent() && category.get().getBooks() != null) {
            return category.get().getBooks();
        }
        return new ArrayList<>();
    }

    // Los datos de la categoría están copiados dentro de los libros, por eso al editarla se actualizan esas copias
    @CacheEvict(cacheNames = {CacheNames.CATEGORIES, CacheNames.BOOKS, CacheNames.BOOK_DETAILS,
            CacheNames.BOOK_QUERIES}, allEntries = true)
    @Override
    public Category updateCategory(String id, CategoryRequest request) {
        Optional<Category> existingCategory = categoryRepository.findById(id);
        if (existingCategory.isPresent()) {
            Category category = existingCategory.get();
            category.setName(request.getName());
            category.setDescription(request.getDescription());
            category = categoryRepository.save(category);
            denormalizationService.propagateCategoryChanges(category);
            return category;
        }
        return null;
    }

    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
    @Override
    public boolean deleteCategory(String id) {
        if (categoryRepository.existsById(id)) {
            categoryRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
