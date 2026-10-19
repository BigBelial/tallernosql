package com.noSQL.claseJava.service;

import com.noSQL.claseJava.dto.CategoryRequest;
import com.noSQL.claseJava.entity.Category;

import java.util.List;
import java.util.Optional;

// Operaciones de las categorías. La implementación está en service/impl/CategoryServiceImpl
public interface CategoryService {

    Category createCategory(CategoryRequest request);

    List<Category> getAllCategories();

    Optional<Category> getCategoryById(String id);

    Optional<Category> getCategoryByName(String name);

    // Libros de la categoría (lista guardada dentro de la categoría)
    List<Category.BookReference> getCategoryBooks(String categoryId);

    // Edita la categoría y copia el cambio a los libros que la tienen. Devuelve null si no existe
    Category updateCategory(String id, CategoryRequest request);

    // Devuelve false si la categoría no existe
    boolean deleteCategory(String id);
}
