package com.noSQL.claseJava.service;

import com.noSQL.claseJava.entity.Book;
import com.noSQL.claseJava.entity.Favorites;
import com.noSQL.claseJava.entity.Loan;
import com.noSQL.claseJava.entity.Review;
import com.noSQL.claseJava.entity.User;
import com.noSQL.claseJava.repository.FavoritesRepository;
import com.noSQL.claseJava.repository.ReviewRepository;
import com.noSQL.claseJava.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

// Esta clase se encarga de que los datos copiados (denormalizados) no queden desactualizados.
// Los datos del libro y del usuario están copiados en préstamos, reseñas, favoritos, en el
// resumen de actividad del usuario y en el préstamo actual del libro. Cuando cambia el
// original, aquí se actualizan todas las copias.
@RequiredArgsConstructor
@Service
public class DenormalizationService {

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final FavoritesRepository favoritesRepository;

    // Devuelve el nombre del autor del libro (o null si el libro no tiene autor)
    public static String authorNameOf(Book book) {
        return book.getAuthor() != null ? book.getAuthor().getFullName() : null;
    }

    // Se llama cuando cambia el título o el autor de un libro
    public void propagateBookChanges(Book book) {
        String bookId = book.getId();
        String title = book.getTitle();
        String authorName = authorNameOf(book);

        // 1. Préstamos de ese libro
        mongoTemplate.updateMulti(query(where("bookId").is(bookId)),
                new Update()
                        .set("bookTitle", title)
                        .set("bookAuthor", authorName)
                        .set("bookUniqueIdentifier", book.getUniqueIdentifier()),
                Loan.class);

        // 2. Reseñas de ese libro
        mongoTemplate.updateMulti(query(where("bookId").is(bookId)),
                new Update().set("bookTitle", title),
                Review.class);

        // 3. Listas de favoritos. Un libro solo puede estar una vez en cada lista,
        //    por eso alcanza con el operador "$" (actualiza el elemento que coincidió)
        mongoTemplate.updateMulti(query(where("books.bookId").is(bookId)),
                new Update()
                        .set("books.$.title", title)
                        .set("books.$.authorName", authorName),
                Favorites.class);

        // 4. Últimos préstamos de cada usuario. Aquí el mismo libro puede aparecer varias veces
        //    (si el usuario lo pidió más de una vez), así que se cambian todos los que coincidan
        for (User user : userRepository.findByRecentLoanBookId(bookId)) {
            List<User.LoanSummary> recentLoans = user.getActivity().getRecentLoans();
            recentLoans.stream()
                    .filter(summary -> bookId.equals(summary.getBookId()))
                    .forEach(summary -> summary.setBookTitle(title));
            mongoTemplate.updateFirst(query(where("id").is(user.getId())),
                    new Update().set("activity.recentLoans", recentLoans),
                    User.class);
        }
    }

    // Se llama cuando cambia el nombre o el email de un usuario
    public void propagateUserChanges(User user) {
        String userId = user.getId();

        // 1. Préstamos del usuario
        mongoTemplate.updateMulti(query(where("userId").is(userId)),
                new Update()
                        .set("userFullName", user.getFullName())
                        .set("userEmail", user.getEmail()),
                Loan.class);

        // 2. Reseñas del usuario
        mongoTemplate.updateMulti(query(where("userId").is(userId)),
                new Update().set("userFullName", user.getFullName()),
                Review.class);

        // 3. Lista de favoritos del usuario
        mongoTemplate.updateMulti(query(where("userId").is(userId)),
                new Update().set("userFullName", user.getFullName()),
                Favorites.class);

        // 4. Libros que el usuario tiene prestados ahora mismo
        mongoTemplate.updateMulti(query(where("currentLoan.userId").is(userId)),
                new Update().set("currentLoan.userFullName", user.getFullName()),
                Book.class);
    }

    // Calcula el promedio de calificaciones y el total de reseñas de un libro
    // con una agregación sobre "reviews", y guarda el resultado dentro del libro
    public void refreshBookRating(String bookId) {
        Aggregation aggregation = newAggregation(
                match(where("bookId").is(bookId)),
                group("bookId")
                        .avg("rating").as("averageRating")
                        .count().as("totalReviews"));

        Document result = mongoTemplate.aggregate(aggregation, Review.class, Document.class)
                .getUniqueMappedResult();

        // Si el libro ya no tiene reseñas, el promedio queda en 0
        double averageRating = 0.0;
        int totalReviews = 0;
        if (result != null) {
            // Se redondea a 2 decimales (ej: 4.333 -> 4.33)
            averageRating = Math.round(result.get("averageRating", Number.class).doubleValue() * 100.0) / 100.0;
            totalReviews = result.get("totalReviews", Number.class).intValue();
        }

        mongoTemplate.updateFirst(query(where("id").is(bookId)),
                new Update()
                        .set("averageRating", averageRating)
                        .set("totalReviews", totalReviews),
                Book.class);
    }

    // Se llama cuando se borra un libro.
    // El historial de préstamos NO se borra a propósito: cada préstamo ya tiene su propia copia del título.
    public void removeBookReferences(String bookId) {
        // Se borran las reseñas del libro y a cada usuario que la escribió se le resta 1 en su contador de reseñas
        for (Review review : reviewRepository.findByBookIdOrderByCreatedAtDesc(bookId)) {
            mongoTemplate.updateFirst(query(where("id").is(review.getUserId())),
                    new Update().inc("activity.totalReviews", -1),
                    User.class);
        }
        reviewRepository.deleteByBookId(bookId);

        // Se saca el libro de todas las listas de favoritos y se ajusta el contador de cada usuario
        for (Favorites favorites : favoritesRepository.findByBooksBookId(bookId)) {
            mongoTemplate.updateFirst(query(where("id").is(favorites.getId())),
                    new Update().pull("books", new Document("bookId", bookId)),
                    Favorites.class);
            mongoTemplate.updateFirst(query(where("id").is(favorites.getUserId())),
                    new Update().inc("activity.favoritesCount", -1),
                    User.class);
        }
    }

    // Se llama cuando se borra un usuario.
    // El historial de préstamos NO se borra a propósito: cada préstamo ya tiene su propia copia del nombre.
    public void removeUserReferences(String userId) {
        favoritesRepository.deleteByUserId(userId);

        // Se borran sus reseñas y se recalcula el promedio de los libros que había reseñado
        List<String> reviewedBookIds = reviewRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(Review::getBookId)
                .distinct()
                .toList();
        reviewRepository.deleteByUserId(userId);
        reviewedBookIds.forEach(this::refreshBookRating);
    }
}
