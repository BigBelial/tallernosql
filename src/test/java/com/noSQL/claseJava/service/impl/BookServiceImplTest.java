package com.noSQL.claseJava.service.impl;

import com.noSQL.claseJava.service.DenormalizationService;

import com.noSQL.claseJava.dto.BookRequest;
import com.noSQL.claseJava.entity.Author;
import com.noSQL.claseJava.entity.Book;
import com.noSQL.claseJava.entity.Category;
import com.noSQL.claseJava.entity.Editorial;
import com.noSQL.claseJava.repository.AuthorRepository;
import com.noSQL.claseJava.repository.BookRepository;
import com.noSQL.claseJava.repository.CategoryRepository;
import com.noSQL.claseJava.repository.EditorialRepository;
import com.noSQL.claseJava.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de crear un libro cuando el autor, la editorial o la categoría ya existían
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookServiceImplTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private AuthorRepository authorRepository;
    @Mock
    private EditorialRepository editorialRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private DenormalizationService denormalizationService;
    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private BookServiceImpl bookService;

    @BeforeEach
    void setUp() {
        // Lo que se guarda se devuelve tal cual; el libro recibe un id
        when(authorRepository.save(any(Author.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(editorialRepository.save(any(Editorial.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            book.setId("book-1");
            return book;
        });
        // Las listas de libros del autor, la editorial y la categoría no importan en estas pruebas
        when(authorRepository.findById(anyString())).thenReturn(Optional.empty());
        when(editorialRepository.findById(anyString())).thenReturn(Optional.empty());
        when(categoryRepository.findById(anyString())).thenReturn(Optional.empty());
    }

    // Caso de la foto: ya existían, pero sin biografía, sin sitio web y sin descripción
    @Test
    void createBookCompletesMissingDataOfExistingAuthorEditorialAndCategory() {
        existing(
                Author.builder().id("author-1").fullName("Gabriel García Márquez").nationality("Colombiana").build(),
                Editorial.builder().id("editorial-1").name("Sudamericana").country("Argentina").build(),
                Category.builder().id("category-1").name("Novela").build());

        Book book = bookService.createBook(request("ESPANIOL", "www", "hola"));

        assertThat(book.getAuthor().getBiography()).isEqualTo("ESPANIOL");
        assertThat(book.getEditorial().getWebsite()).isEqualTo("www");
        assertThat(book.getCategories().get(0).getDescription()).isEqualTo("hola");
        verify(authorRepository).save(argThat(author -> "ESPANIOL".equals(author.getBiography())));
        verify(editorialRepository).save(argThat(editorial -> "www".equals(editorial.getWebsite())));
        verify(categoryRepository).save(argThat(category -> "hola".equals(category.getDescription())));
        // Los otros libros que ya tenían ese autor, editorial o categoría también reciben los datos nuevos
        verify(denormalizationService).propagateAuthorChanges(argThat(author -> "ESPANIOL".equals(author.getBiography())), eq(false));
        verify(denormalizationService).propagateEditorialChanges(argThat(editorial -> "www".equals(editorial.getWebsite())));
        verify(denormalizationService).propagateCategoryChanges(argThat(category -> "hola".equals(category.getDescription())));
    }

    // Si el dato ya estaba lleno, se respeta el guardado (para cambiarlo se usa PUT /api/authors/{id})
    @Test
    void createBookKeepsDataThatAlreadyExisted() {
        existing(
                Author.builder().id("author-1").fullName("Gabriel García Márquez")
                        .nationality("Colombiana").biography("Original").build(),
                Editorial.builder().id("editorial-1").name("Sudamericana")
                        .country("Argentina").website("sudamericana.com").build(),
                Category.builder().id("category-1").name("Novela").description("Ficción").build());

        Book book = bookService.createBook(request("Otra", "www", "hola"));

        assertThat(book.getAuthor().getBiography()).isEqualTo("Original");
        assertThat(book.getEditorial().getWebsite()).isEqualTo("sudamericana.com");
        assertThat(book.getCategories().get(0).getDescription()).isEqualTo("Ficción");
        verify(authorRepository, never()).save(any(Author.class));
        verify(editorialRepository, never()).save(any(Editorial.class));
        verify(categoryRepository, never()).save(any(Category.class));
        verify(denormalizationService, never()).propagateAuthorChanges(any(Author.class), anyBoolean());
        verify(denormalizationService, never()).propagateEditorialChanges(any(Editorial.class));
        verify(denormalizationService, never()).propagateCategoryChanges(any(Category.class));
    }

    private void existing(Author author, Editorial editorial, Category category) {
        when(authorRepository.findByFullName(author.getFullName())).thenReturn(Optional.of(author));
        when(editorialRepository.findByName(editorial.getName())).thenReturn(Optional.of(editorial));
        when(categoryRepository.findByName(category.getName())).thenReturn(Optional.of(category));
    }

    private BookRequest request(String biography, String website, String description) {
        List<Book.Category> categories = new ArrayList<>();
        categories.add(Book.Category.builder().name("Novela").description(description).build());
        return BookRequest.builder()
                .uniqueIdentifier("ISBN-0004")
                .title("Cien años de soledad")
                .synopsis("La historia de la familia Buendía")
                .publicationYear(1967)
                .author(Book.Author.builder().fullName("Gabriel García Márquez").nationality("Colombiana").biography(biography).build())
                .editorial(Book.Editorial.builder().name("Sudamericana").country("Argentina").website(website).build())
                .categories(categories)
                .build();
    }
}
