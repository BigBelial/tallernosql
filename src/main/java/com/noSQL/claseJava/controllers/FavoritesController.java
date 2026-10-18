package com.noSQL.claseJava.controllers;

import com.noSQL.claseJava.entity.Favorites;
import com.noSQL.claseJava.service.FavoritesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Los favoritos dependen del usuario, por eso las rutas van debajo de /api/users/{userId}
@RestController
@RequestMapping("/api/users/{userId}/favorites")
@RequiredArgsConstructor
public class FavoritesController {

    private final FavoritesService favoritesService;

    @GetMapping
    public ResponseEntity<Favorites> getFavorites(@PathVariable String userId) {
        return ResponseEntity.ok(favoritesService.getFavorites(userId));
    }

    @PostMapping("/{bookId}")
    public ResponseEntity<Favorites> addFavorite(@PathVariable String userId, @PathVariable String bookId) {
        Favorites favorites = favoritesService.addFavorite(userId, bookId);
        return ResponseEntity.status(HttpStatus.CREATED).body(favorites);
    }

    @DeleteMapping("/{bookId}")
    public ResponseEntity<Favorites> removeFavorite(@PathVariable String userId, @PathVariable String bookId) {
        return ResponseEntity.ok(favoritesService.removeFavorite(userId, bookId));
    }
}
