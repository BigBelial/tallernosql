package com.noSQL.claseJava.service.impl;

import com.noSQL.claseJava.service.EditorialService;
import com.noSQL.claseJava.service.DenormalizationService;

import com.noSQL.claseJava.config.CacheNames;
import com.noSQL.claseJava.dto.EditorialRequest;
import com.noSQL.claseJava.entity.Editorial;
import com.noSQL.claseJava.repository.EditorialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class EditorialServiceImpl implements EditorialService {

    private final EditorialRepository editorialRepository;
    private final DenormalizationService denormalizationService;

    @CacheEvict(cacheNames = CacheNames.EDITORIALS, allEntries = true)
    @Override
    public Editorial createEditorial(EditorialRequest request) {
        Editorial editorial = Editorial.builder()
                .name(request.getName())
                .country(request.getCountry())
                .website(request.getWebsite())
                .build();
        return editorialRepository.save(editorial);
    }

    @Cacheable(CacheNames.EDITORIALS)
    @Override
    public List<Editorial> getAllEditorials() {
        return editorialRepository.findAll();
    }

    @Cacheable(cacheNames = CacheNames.EDITORIALS, unless = "#result == null")
    @Override
    public Optional<Editorial> getEditorialById(String id) {
        return editorialRepository.findById(id);
    }

    @Cacheable(cacheNames = CacheNames.EDITORIALS, unless = "#result == null")
    @Override
    public Optional<Editorial> getEditorialByName(String name) {
        return editorialRepository.findByName(name);
    }

    // Se devuelve un ArrayList (y no List.of()) porque el resultado se guarda en Redis
    @Cacheable(CacheNames.EDITORIALS)
    @Override
    public List<Editorial.BookReference> getEditorialBooks(String editorialId) {
        Optional<Editorial> editorial = editorialRepository.findById(editorialId);
        if (editorial.isPresent() && editorial.get().getBooks() != null) {
            return editorial.get().getBooks();
        }
        return new ArrayList<>();
    }

    // Los datos de la editorial están copiados dentro de los libros, por eso al editarla se actualizan esas copias
    @CacheEvict(cacheNames = {CacheNames.EDITORIALS, CacheNames.BOOKS, CacheNames.BOOK_DETAILS,
            CacheNames.BOOK_QUERIES}, allEntries = true)
    @Override
    public Editorial updateEditorial(String id, EditorialRequest request) {
        Optional<Editorial> existingEditorial = editorialRepository.findById(id);
        if (existingEditorial.isPresent()) {
            Editorial editorial = existingEditorial.get();
            editorial.setName(request.getName());
            editorial.setCountry(request.getCountry());
            editorial.setWebsite(request.getWebsite());
            editorial = editorialRepository.save(editorial);
            denormalizationService.propagateEditorialChanges(editorial);
            return editorial;
        }
        return null;
    }

    @CacheEvict(cacheNames = CacheNames.EDITORIALS, allEntries = true)
    @Override
    public boolean deleteEditorial(String id) {
        if (editorialRepository.existsById(id)) {
            editorialRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
