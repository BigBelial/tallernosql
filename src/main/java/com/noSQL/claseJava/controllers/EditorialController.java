package com.noSQL.claseJava.controllers;

import com.noSQL.claseJava.dto.EditorialRequest;
import com.noSQL.claseJava.entity.Editorial;
import com.noSQL.claseJava.service.EditorialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/editorials")
@RequiredArgsConstructor
public class EditorialController {

    private final EditorialService editorialService;

    @PostMapping
    public ResponseEntity<Editorial> createEditorial(@RequestBody EditorialRequest request) {
        Editorial createdEditorial = editorialService.createEditorial(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEditorial);
    }

    @GetMapping
    public ResponseEntity<List<Editorial>> getAllEditorials() {
        List<Editorial> editorials = editorialService.getAllEditorials();
        return ResponseEntity.ok(editorials);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Editorial> getEditorialById(@PathVariable String id) {
        Optional<Editorial> editorial = editorialService.getEditorialById(id);
        return editorial.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search/{name}")
    public ResponseEntity<Editorial> getEditorialByName(@PathVariable String name) {
        Optional<Editorial> editorial = editorialService.getEditorialByName(name);
        return editorial.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/books")
    public ResponseEntity<List<Editorial.BookReference>> getEditorialBooks(@PathVariable String id) {
        List<Editorial.BookReference> books = editorialService.getEditorialBooks(id);
        return ResponseEntity.ok(books);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Editorial> updateEditorial(@PathVariable String id, @RequestBody EditorialRequest request) {
        Editorial updatedEditorial = editorialService.updateEditorial(id, request);
        if (updatedEditorial != null) {
            return ResponseEntity.ok(updatedEditorial);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEditorial(@PathVariable String id) {
        if (editorialService.deleteEditorial(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
