package com.noSQL.claseJava.service;

import com.noSQL.claseJava.entity.Favorites;

// Operaciones de la lista de favoritos de cada usuario. La implementación está en service/impl/FavoritesServiceImpl
public interface FavoritesService {

    // Lista de favoritos del usuario (vacía si todavía no tiene)
    Favorites getFavorites(String userId);

    // Agrega un libro. No se repiten libros y hay un máximo por usuario
    Favorites addFavorite(String userId, String bookId);

    Favorites removeFavorite(String userId, String bookId);
}
