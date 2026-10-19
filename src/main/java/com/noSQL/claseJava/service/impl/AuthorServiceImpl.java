package com.noSQL.claseJava.service.impl;

import com.noSQL.claseJava.service.AuthorService;
import com.noSQL.claseJava.service.DenormalizationService;

import com.noSQL.claseJava.config.CacheNames;
import com.noSQL.claseJava.dto.AuthorRequest;
import com.noSQL.claseJava.entity.Author;
import com.noSQL.claseJava.repository.AuthorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class AuthorServiceImpl implements AuthorService {

    private final AuthorRepository authorRepository;
    private final DenormalizationService denormalizationService;

    @CacheEvict(cacheNames = CacheNames.AUTHORS, allEntries = true)
    @Override
    public Author createAuthor(AuthorRequest request) {
        Author author = Author.builder()
                .fullName(request.getFullName())
                .nationality(request.getNationality())
                .biography(request.getBiography())
                .build();
        return authorRepository.save(author);
    }

    @Cacheable(CacheNames.AUTHORS)
    @Override
    public List<Author> getAllAuthors() {
        return authorRepository.findAll();
    }

    @Cacheable(cacheNames = CacheNames.AUTHORS, unless = "#result == null")
    @Override
    public Optional<Author> getAuthorById(String id) {
        return authorRepository.findById(id);
    }

    @Cacheable(cacheNames = CacheNames.AUTHORS, unless = "#result == null")
    @Override
    public Optional<Author> getAuthorByName(String fullName) {
        return authorRepository.findByFullName(fullName);
    }

    // Se devuelve un ArrayList (y no List.of()) porque el resultado se guarda en Redis
    @Cacheable(CacheNames.AUTHORS)
    @Override
    public List<Author.BookReference> getAuthorBooks(String authorId) {
        Optional<Author> author = authorRepository.findById(authorId);
        if (author.isPresent() && author.get().getBooks() != null) {
            return author.get().getBooks();
        }
        return new ArrayList<>();
    }

    // Los datos del autor están copiados dentro de los libros (y su nombre en préstamos y favoritos),
    // por eso al editarlo se actualizan esas copias y se limpian esas cachés
    @CacheEvict(cacheNames = {CacheNames.AUTHORS, CacheNames.BOOKS, CacheNames.BOOK_DETAILS, CacheNames.BOOK_QUERIES,
            CacheNames.LOANS, CacheNames.FAVORITES, CacheNames.LOAN_STATS}, allEntries = true)
    @Override
    public Author updateAuthor(String id, AuthorRequest request) {
        Optional<Author> existingAuthor = authorRepository.findById(id);
        if (existingAuthor.isPresent()) {
            Author author = existingAuthor.get();
            boolean nameChanged = !Objects.equals(author.getFullName(), request.getFullName());
            author.setFullName(request.getFullName());
            author.setNationality(request.getNationality());
            author.setBiography(request.getBiography());
            author = authorRepository.save(author);
            denormalizationService.propagateAuthorChanges(author, nameChanged);
            return author;
        }
        return null;
    }

    @CacheEvict(cacheNames = CacheNames.AUTHORS, allEntries = true)
    @Override
    public boolean deleteAuthor(String id) {
        if (authorRepository.existsById(id)) {
            authorRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
