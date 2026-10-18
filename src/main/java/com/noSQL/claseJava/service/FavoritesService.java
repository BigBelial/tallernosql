package com.noSQL.claseJava.service;

import com.noSQL.claseJava.config.CacheNames;
import com.noSQL.claseJava.entity.Book;
import com.noSQL.claseJava.entity.Favorites;
import com.noSQL.claseJava.entity.User;
import com.noSQL.claseJava.exception.BusinessException;
import com.noSQL.claseJava.exception.ResourceNotFoundException;
import com.noSQL.claseJava.repository.BookRepository;
import com.noSQL.claseJava.repository.FavoritesRepository;
import com.noSQL.claseJava.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@RequiredArgsConstructor
@Service
public class FavoritesService {

    // Máximo de libros en la lista de favoritos de un usuario (así el documento no crece sin límite)
    static final int MAX_FAVORITES = 50;

    private final FavoritesRepository favoritesRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final MongoTemplate mongoTemplate;

    @Cacheable(CacheNames.FAVORITES)
    public Favorites getFavorites(String userId) {
        User user = findUser(userId);
        // Si el usuario todavía no tiene favoritos, se devuelve una lista vacía
        return favoritesRepository.findByUserId(userId)
                .orElseGet(() -> Favorites.builder()
                        .userId(userId)
                        .userFullName(user.getFullName())
                        .build());
    }

    @CacheEvict(cacheNames = {CacheNames.FAVORITES, CacheNames.USERS}, allEntries = true)
    public Favorites addFavorite(String userId, String bookId) {
        User user = findUser(userId);
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", bookId));

        // La lista se crea la primera vez que el usuario agrega un favorito
        Favorites favorites = favoritesRepository.findByUserId(userId)
                .orElseGet(() -> favoritesRepository.save(Favorites.builder()
                        .userId(userId)
                        .userFullName(user.getFullName())
                        .updatedAt(LocalDateTime.now())
                        .build()));

        // Reglas de negocio: no repetir libros y no pasar el máximo
        if (favorites.getBooks() != null && favorites.getBooks().stream().anyMatch(fb -> bookId.equals(fb.getBookId()))) {
            throw new BusinessException("Book '" + book.getTitle() + "' is already in the favorites list");
        }
        if (favorites.getBooks() != null && favorites.getBooks().size() >= MAX_FAVORITES) {
            throw new BusinessException("The favorites list cannot have more than " + MAX_FAVORITES + " books");
        }

        // Se copian el título y el autor dentro de la lista (denormalización)
        Favorites.FavoriteBook entry = Favorites.FavoriteBook.builder()
                .bookId(book.getId())
                .title(book.getTitle())
                .authorName(DenormalizationService.authorNameOf(book))
                .addedAt(LocalDateTime.now())
                .build();

        // El libro solo se agrega si todavía no está en la lista (la condición va en la misma consulta,
        // así dos peticiones al mismo tiempo no lo agregan dos veces)
        Favorites updated = mongoTemplate.findAndModify(
                query(where("userId").is(userId).and("books.bookId").ne(bookId)),
                new Update().push("books", entry).set("updatedAt", LocalDateTime.now()),
                FindAndModifyOptions.options().returnNew(true),
                Favorites.class);
        if (updated == null) {
            throw new BusinessException("Book '" + book.getTitle() + "' is already in the favorites list");
        }

        // Se actualiza el contador que está en el resumen del usuario
        mongoTemplate.updateFirst(query(where("id").is(userId)),
                new Update().inc("activity.favoritesCount", 1), User.class);
        return updated;
    }

    @CacheEvict(cacheNames = {CacheNames.FAVORITES, CacheNames.USERS}, allEntries = true)
    public Favorites removeFavorite(String userId, String bookId) {
        findUser(userId);
        Favorites updated = mongoTemplate.findAndModify(
                query(where("userId").is(userId).and("books.bookId").is(bookId)),
                new Update().pull("books", new Document("bookId", bookId)).set("updatedAt", LocalDateTime.now()),
                FindAndModifyOptions.options().returnNew(true),
                Favorites.class);
        if (updated == null) {
            throw new ResourceNotFoundException("Book " + bookId + " is not in the favorites list of user " + userId);
        }

        mongoTemplate.updateFirst(query(where("id").is(userId)),
                new Update().inc("activity.favoritesCount", -1), User.class);
        return updated;
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
