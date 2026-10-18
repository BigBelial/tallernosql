package com.noSQL.claseJava.service;

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
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class AuthorService {

    private final AuthorRepository authorRepository;

    @CacheEvict(cacheNames = CacheNames.AUTHORS, allEntries = true)
    public Author createAuthor(AuthorRequest request) {
        Author author = Author.builder()
                .fullName(request.getFullName())
                .nationality(request.getNationality())
                .biography(request.getBiography())
                .build();
        return authorRepository.save(author);
    }

    @Cacheable(CacheNames.AUTHORS)
    public List<Author> getAllAuthors() {
        return authorRepository.findAll();
    }

    @Cacheable(cacheNames = CacheNames.AUTHORS, unless = "#result == null")
    public Optional<Author> getAuthorById(String id) {
        return authorRepository.findById(id);
    }

    @Cacheable(cacheNames = CacheNames.AUTHORS, unless = "#result == null")
    public Optional<Author> getAuthorByName(String fullName) {
        return authorRepository.findByFullName(fullName);
    }

    // Se devuelve un ArrayList (y no List.of()) porque el resultado se guarda en Redis
    @Cacheable(CacheNames.AUTHORS)
    public List<Author.BookReference> getAuthorBooks(String authorId) {
        Optional<Author> author = authorRepository.findById(authorId);
        if (author.isPresent() && author.get().getBooks() != null) {
            return author.get().getBooks();
        }
        return new ArrayList<>();
    }

    @CacheEvict(cacheNames = CacheNames.AUTHORS, allEntries = true)
    public Author updateAuthor(String id, AuthorRequest request) {
        Optional<Author> existingAuthor = authorRepository.findById(id);
        if (existingAuthor.isPresent()) {
            Author author = existingAuthor.get();
            author.setFullName(request.getFullName());
            author.setNationality(request.getNationality());
            author.setBiography(request.getBiography());
            return authorRepository.save(author);
        }
        return null;
    }

    @CacheEvict(cacheNames = CacheNames.AUTHORS, allEntries = true)
    public boolean deleteAuthor(String id) {
        if (authorRepository.existsById(id)) {
            authorRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
