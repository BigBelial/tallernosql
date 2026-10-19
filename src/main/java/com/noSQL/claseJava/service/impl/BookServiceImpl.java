package com.noSQL.claseJava.service.impl;

import com.noSQL.claseJava.service.BookService;
import com.noSQL.claseJava.service.DenormalizationService;

import com.noSQL.claseJava.config.CacheNames;
import com.noSQL.claseJava.dto.BookDetailResponse;
import com.noSQL.claseJava.dto.BookRequest;
import com.noSQL.claseJava.entity.Book;
import com.noSQL.claseJava.entity.Author;
import com.noSQL.claseJava.entity.Editorial;
import com.noSQL.claseJava.entity.Category;
import com.noSQL.claseJava.exception.BusinessException;
import com.noSQL.claseJava.repository.BookRepository;
import com.noSQL.claseJava.repository.AuthorRepository;
import com.noSQL.claseJava.repository.EditorialRepository;
import com.noSQL.claseJava.repository.CategoryRepository;
import com.noSQL.claseJava.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@RequiredArgsConstructor
@Service
public class BookServiceImpl implements BookService {

    private static final int MAX_RESULTS = 50;

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final EditorialRepository editorialRepository;
    private final CategoryRepository categoryRepository;
    private final ReviewRepository reviewRepository;
    private final DenormalizationService denormalizationService;
    private final MongoTemplate mongoTemplate;

    // Crear un libro también cambia las listas de libros del autor, la editorial y las categorías,
    // y si se completan datos del autor (o editorial o categoría) cambian los otros libros que lo tienen
    @CacheEvict(cacheNames = {CacheNames.BOOKS, CacheNames.BOOK_DETAILS, CacheNames.BOOK_QUERIES, CacheNames.LOAN_STATS,
            CacheNames.AUTHORS, CacheNames.EDITORIALS, CacheNames.CATEGORIES}, allEntries = true)
    @Override
    public Book createBook(BookRequest request) {
        // Buscar el autor por nombre: si no existe se crea, y si le faltaban datos se completan
        Book.Author authorData = request.getAuthor();
        Author author = resolveAuthor(authorData);
        authorData.setId(author.getId());
        authorData.setFullName(author.getFullName());
        authorData.setNationality(author.getNationality());
        authorData.setBiography(author.getBiography());

        // Buscar la editorial por nombre: si no existe se crea, y si le faltaban datos se completan
        Book.Editorial editorialData = request.getEditorial();
        Editorial editorial = resolveEditorial(editorialData);
        editorialData.setId(editorial.getId());
        editorialData.setName(editorial.getName());
        editorialData.setCountry(editorial.getCountry());
        editorialData.setWebsite(editorial.getWebsite());

        // Buscar cada categoría por nombre: si no existe se crea, y si le faltaban datos se completan
        if (request.getCategories() != null) {
            for (Book.Category catData : request.getCategories()) {
                Category category = resolveCategory(catData);
                catData.setId(category.getId());
                catData.setName(category.getName());
                catData.setDescription(category.getDescription());
            }
        }

        // Crear el libro. Un libro nuevo siempre empieza disponible, sin préstamos y sin reseñas.
        Book book = Book.builder()
                .uniqueIdentifier(request.getUniqueIdentifier())
                .title(request.getTitle())
                .synopsis(request.getSynopsis())
                .publicationYear(request.getPublicationYear())
                .available(true)
                .loaned(false)
                .author(authorData)
                .editorial(editorialData)
                .categories(request.getCategories())
                .build();
        book = bookRepository.save(book);

        // Agregar la referencia del libro en el autor
        addBookToAuthor(book.getAuthor().getId(), book.getId(), book.getTitle());

        // Agregar la referencia del libro en la editorial
        addBookToEditorial(book.getEditorial().getId(), book.getId(), book.getTitle());

        // Agregar la referencia del libro en cada categoría
        if (request.getCategories() != null) {
            for (Book.Category cat : request.getCategories()) {
                addBookToCategory(cat.getId(), book.getId(), book.getTitle());
            }
        }

        return book;
    }

    // Antes, si el autor ya existía se usaban solo sus datos guardados, y los que llegaban en la petición
    // se perdían (por ejemplo la biografía). Ahora los campos que estaban vacíos se completan con los de la
    // petición y el cambio se copia a los demás libros de ese autor. Los datos que ya estaban llenos no se
    // cambian aquí: para eso está PUT /api/authors/{id}.
    private Author resolveAuthor(Book.Author data) {
        Optional<Author> existing = authorRepository.findByFullName(data.getFullName());
        if (existing.isEmpty()) {
            return authorRepository.save(Author.builder()
                    .fullName(data.getFullName())
                    .nationality(data.getNationality())
                    .biography(data.getBiography())
                    .build());
        }
        Author author = existing.get();
        boolean completed = false;
        if (isBlank(author.getNationality()) && !isBlank(data.getNationality())) {
            author.setNationality(data.getNationality());
            completed = true;
        }
        if (isBlank(author.getBiography()) && !isBlank(data.getBiography())) {
            author.setBiography(data.getBiography());
            completed = true;
        }
        if (completed) {
            author = authorRepository.save(author);
            denormalizationService.propagateAuthorChanges(author, false);
        }
        return author;
    }

    // Igual que con el autor: se crea si no existe y se completan los datos que le faltaban
    private Editorial resolveEditorial(Book.Editorial data) {
        Optional<Editorial> existing = editorialRepository.findByName(data.getName());
        if (existing.isEmpty()) {
            return editorialRepository.save(Editorial.builder()
                    .name(data.getName())
                    .country(data.getCountry())
                    .website(data.getWebsite())
                    .build());
        }
        Editorial editorial = existing.get();
        boolean completed = false;
        if (isBlank(editorial.getCountry()) && !isBlank(data.getCountry())) {
            editorial.setCountry(data.getCountry());
            completed = true;
        }
        if (isBlank(editorial.getWebsite()) && !isBlank(data.getWebsite())) {
            editorial.setWebsite(data.getWebsite());
            completed = true;
        }
        if (completed) {
            editorial = editorialRepository.save(editorial);
            denormalizationService.propagateEditorialChanges(editorial);
        }
        return editorial;
    }

    // Igual que con el autor: se crea si no existe y se completa la descripción si le faltaba
    private Category resolveCategory(Book.Category data) {
        Optional<Category> existing = categoryRepository.findByName(data.getName());
        if (existing.isEmpty()) {
            return categoryRepository.save(Category.builder()
                    .name(data.getName())
                    .description(data.getDescription())
                    .build());
        }
        Category category = existing.get();
        if (isBlank(category.getDescription()) && !isBlank(data.getDescription())) {
            category.setDescription(data.getDescription());
            category = categoryRepository.save(category);
            denormalizationService.propagateCategoryChanges(category);
        }
        return category;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void addBookToAuthor(String authorId, String bookId, String bookTitle) {
        Optional<Author> author = authorRepository.findById(authorId);
        if (author.isPresent()) {
            Author a = author.get();
            if (a.getBooks() == null) {
                a.setBooks(new java.util.ArrayList<>());
            }
            a.getBooks().add(Author.BookReference.builder()
                    .bookId(bookId)
                    .title(bookTitle)
                    .build());
            authorRepository.save(a);
        }
    }

    private void addBookToEditorial(String editorialId, String bookId, String bookTitle) {
        Optional<Editorial> editorial = editorialRepository.findById(editorialId);
        if (editorial.isPresent()) {
            Editorial e = editorial.get();
            if (e.getBooks() == null) {
                e.setBooks(new java.util.ArrayList<>());
            }
            e.getBooks().add(Editorial.BookReference.builder()
                    .bookId(bookId)
                    .title(bookTitle)
                    .build());
            editorialRepository.save(e);
        }
    }

    private void addBookToCategory(String categoryId, String bookId, String bookTitle) {
        Optional<Category> category = categoryRepository.findById(categoryId);
        if (category.isPresent()) {
            Category c = category.get();
            if (c.getBooks() == null) {
                c.setBooks(new java.util.ArrayList<>());
            }
            c.getBooks().add(Category.BookReference.builder()
                    .bookId(bookId)
                    .title(bookTitle)
                    .build());
            categoryRepository.save(c);
        }
    }

    @Cacheable(CacheNames.BOOKS)
    @Override
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    @Override
    public Optional<Book> getBookById(String id) {
        return bookRepository.findById(id);
    }

    // Detalle del libro junto con sus reseñas. Cada reseña ya trae el nombre del usuario
    // copiado, así que no hay que ir a buscar a "users".
    @Cacheable(cacheNames = CacheNames.BOOK_DETAILS, unless = "#result == null")
    @Override
    public Optional<BookDetailResponse> getBookDetail(String id) {
        return bookRepository.findById(id)
                .map(book -> BookDetailResponse.builder()
                        .book(book)
                        .reviews(reviewRepository.findByBookIdOrderByCreatedAtDesc(id))
                        .build());
    }

    @Cacheable(CacheNames.BOOK_QUERIES)
    @Override
    public List<Book> getBooksByAuthor(String authorFullName) {
        return bookRepository.findByAuthorFullName(authorFullName);
    }

    @Cacheable(CacheNames.BOOK_QUERIES)
    @Override
    public List<Book> getBooksByEditorial(String editorialName) {
        return bookRepository.findByEditorialName(editorialName);
    }

    @Cacheable(CacheNames.BOOK_QUERIES)
    @Override
    public List<Book> getBooksByCategory(String categoryName) {
        return bookRepository.findByCategoriesName(categoryName);
    }

    @Cacheable(CacheNames.BOOK_QUERIES)
    @Override
    public List<Book> getAvailableBooks() {
        return bookRepository.findAvailableBooks();
    }

    @Cacheable(CacheNames.BOOK_QUERIES)
    @Override
    public List<Book> getLoanedBooks() {
        return bookRepository.findByLoaned(true);
    }

    @Cacheable(CacheNames.BOOK_QUERIES)
    @Override
    public List<Book> getPopularBooks(int limit) {
        return bookRepository.findPopularBooks(PageRequest.of(0, normalizeLimit(limit)));
    }

    @Cacheable(CacheNames.BOOK_QUERIES)
    @Override
    public List<Book> getTopRatedBooks(int limit, int minReviews) {
        return bookRepository.findTopRatedBooks(Math.max(1, minReviews), PageRequest.of(0, normalizeLimit(limit)));
    }

    // El límite queda entre 1 y 50 para no devolver listas gigantes
    private int normalizeLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_RESULTS);
    }

    @CacheEvict(cacheNames = {CacheNames.BOOKS, CacheNames.BOOK_DETAILS, CacheNames.BOOK_QUERIES,
            CacheNames.LOANS, CacheNames.REVIEWS, CacheNames.FAVORITES, CacheNames.USERS,
            CacheNames.LOAN_STATS, CacheNames.AUTHORS, CacheNames.EDITORIALS, CacheNames.CATEGORIES}, allEntries = true)
    @Override
    public Book updateBook(String id, BookRequest request) {
        Optional<Book> existingBook = bookRepository.findById(id);
        if (existingBook.isPresent()) {
            String oldTitle = existingBook.get().getTitle();
            String oldAuthorName = DenormalizationService.authorNameOf(existingBook.get());
            String oldUniqueIdentifier = existingBook.get().getUniqueIdentifier();
            String newTitle = request.getTitle();

            // Solo se cambian los campos que se pueden editar. Se usa $set en vez de save()
            // para no pisar los campos que manejan los préstamos y las reseñas
            // (loaned, available, currentLoan, totalLoans, averageRating, totalReviews).
            Update update = new Update()
                    .set("uniqueIdentifier", request.getUniqueIdentifier())
                    .set("title", newTitle)
                    .set("synopsis", request.getSynopsis())
                    .set("publicationYear", request.getPublicationYear())
                    .set("author", request.getAuthor())
                    .set("editorial", request.getEditorial())
                    .set("categories", request.getCategories());
            Book book = mongoTemplate.findAndModify(query(where("id").is(id)), update,
                    FindAndModifyOptions.options().returnNew(true), Book.class);
            if (book == null) {
                return null;
            }

            // ═══════════════════════════════════════════════════════════════════════════════
            // 🔄 BLOQUE: ACTUALIZACIÓN EN CASCADA DE REFERENCIAS DENORMALIZADAS (ACTIVADO)
            // ═══════════════════════════════════════════════════════════════════════════════
            // Sin este bloque: el título cambia en 'books' pero NO en las copias que hay en
            //                  author.books, editorial.books, categories.books, préstamos,
            //                  reseñas, favoritos y en los últimos préstamos de cada usuario.
            //
            // Con este bloque: el título se actualiza en TODOS los lugares.
            //                  Los datos quedan consistentes, aunque es más lento (varias escrituras).
            // ═══════════════════════════════════════════════════════════════════════════════
            if (!Objects.equals(oldTitle, newTitle)) {
                // Actualizar en el autor
                if (book.getAuthor() != null) {
                    updateBookTitleInAuthor(book.getAuthor().getId(), id, newTitle);
                }

                // Actualizar en la editorial
                if (book.getEditorial() != null) {
                    updateBookTitleInEditorial(book.getEditorial().getId(), id, newTitle);
                }

                // Actualizar en todas las categorías
                if (book.getCategories() != null) {
                    for (Book.Category cat : book.getCategories()) {
                        updateBookTitleInCategory(cat.getId(), id, newTitle);
                    }
                }
            }

            // Préstamos, reseñas, favoritos y resumen de usuarios también guardan el título, el autor y el ISBN
            if (!Objects.equals(oldTitle, newTitle)
                    || !Objects.equals(oldAuthorName, DenormalizationService.authorNameOf(book))
                    || !Objects.equals(oldUniqueIdentifier, book.getUniqueIdentifier())) {
                denormalizationService.propagateBookChanges(book);
            }

            return book;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MÉTODOS AUXILIARES PARA LA ACTUALIZACIÓN EN CASCADA
    // ═══════════════════════════════════════════════════════════════════════════════

    private void updateBookTitleInAuthor(String authorId, String bookId, String newTitle) {
        if (authorId == null) {
            return;
        }
        Optional<Author> author = authorRepository.findById(authorId);
        if (author.isPresent()) {
            Author a = author.get();
            if (a.getBooks() != null) {
                a.getBooks().stream()
                    .filter(br -> bookId.equals(br.getBookId()))
                    .forEach(br -> br.setTitle(newTitle));
                authorRepository.save(a);
            }
        }
    }

    private void updateBookTitleInEditorial(String editorialId, String bookId, String newTitle) {
        if (editorialId == null) {
            return;
        }
        Optional<Editorial> editorial = editorialRepository.findById(editorialId);
        if (editorial.isPresent()) {
            Editorial e = editorial.get();
            if (e.getBooks() != null) {
                e.getBooks().stream()
                    .filter(br -> bookId.equals(br.getBookId()))
                    .forEach(br -> br.setTitle(newTitle));
                editorialRepository.save(e);
            }
        }
    }

    private void updateBookTitleInCategory(String categoryId, String bookId, String newTitle) {
        if (categoryId == null) {
            return;
        }
        Optional<Category> category = categoryRepository.findById(categoryId);
        if (category.isPresent()) {
            Category c = category.get();
            if (c.getBooks() != null) {
                c.getBooks().stream()
                    .filter(br -> bookId.equals(br.getBookId()))
                    .forEach(br -> br.setTitle(newTitle));
                categoryRepository.save(c);
            }
        }
    }

    @CacheEvict(cacheNames = {CacheNames.BOOKS, CacheNames.BOOK_DETAILS, CacheNames.BOOK_QUERIES,
            CacheNames.REVIEWS, CacheNames.FAVORITES, CacheNames.USERS, CacheNames.LOAN_STATS,
            CacheNames.AUTHORS, CacheNames.EDITORIALS, CacheNames.CATEGORIES}, allEntries = true)
    @Override
    public boolean deleteBook(String id) {
        Optional<Book> book = bookRepository.findById(id);
        if (book.isPresent()) {
            Book b = book.get();
            // Regla de negocio: no se puede borrar un libro que alguien tiene prestado
            if (Boolean.TRUE.equals(b.getLoaned())) {
                throw new BusinessException("Book '" + b.getTitle() + "' is on loan and cannot be deleted");
            }
            // Se borra solo si sigue sin estar prestado (por si alguien lo prestó justo en este momento)
            long deleted = mongoTemplate.remove(query(where("id").is(id).and("loaned").ne(true)), Book.class)
                    .getDeletedCount();
            if (deleted == 0) {
                throw new BusinessException("Book '" + b.getTitle() + "' is on loan and cannot be deleted");
            }

            // Quitar del autor
            if (b.getAuthor() != null) {
                removeBookFromAuthor(b.getAuthor().getId(), id);
            }
            // Quitar de la editorial
            if (b.getEditorial() != null) {
                removeBookFromEditorial(b.getEditorial().getId(), id);
            }
            // Quitar de las categorías
            if (b.getCategories() != null) {
                for (Book.Category cat : b.getCategories()) {
                    removeBookFromCategory(cat.getId(), id);
                }
            }
            // Quitar las reseñas y sacarlo de las listas de favoritos
            denormalizationService.removeBookReferences(id);
            return true;
        }
        return false;
    }

    private void removeBookFromAuthor(String authorId, String bookId) {
        if (authorId == null) {
            return;
        }
        Optional<Author> author = authorRepository.findById(authorId);
        if (author.isPresent()) {
            Author a = author.get();
            if (a.getBooks() != null) {
                a.getBooks().removeIf(br -> bookId.equals(br.getBookId()));
                authorRepository.save(a);
            }
        }
    }

    private void removeBookFromEditorial(String editorialId, String bookId) {
        if (editorialId == null) {
            return;
        }
        Optional<Editorial> editorial = editorialRepository.findById(editorialId);
        if (editorial.isPresent()) {
            Editorial e = editorial.get();
            if (e.getBooks() != null) {
                e.getBooks().removeIf(br -> bookId.equals(br.getBookId()));
                editorialRepository.save(e);
            }
        }
    }

    private void removeBookFromCategory(String categoryId, String bookId) {
        if (categoryId == null) {
            return;
        }
        Optional<Category> category = categoryRepository.findById(categoryId);
        if (category.isPresent()) {
            Category c = category.get();
            if (c.getBooks() != null) {
                c.getBooks().removeIf(br -> bookId.equals(br.getBookId()));
                categoryRepository.save(c);
            }
        }
    }
}
