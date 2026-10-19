package com.noSQL.claseJava.service;

import com.noSQL.claseJava.dto.EditorialRequest;
import com.noSQL.claseJava.entity.Editorial;

import java.util.List;
import java.util.Optional;

// Operaciones de las editoriales. La implementación está en service/impl/EditorialServiceImpl
public interface EditorialService {

    Editorial createEditorial(EditorialRequest request);

    List<Editorial> getAllEditorials();

    Optional<Editorial> getEditorialById(String id);

    Optional<Editorial> getEditorialByName(String name);

    // Libros de la editorial (lista guardada dentro de la editorial)
    List<Editorial.BookReference> getEditorialBooks(String editorialId);

    // Edita la editorial y copia el cambio a los libros que la tienen. Devuelve null si no existe
    Editorial updateEditorial(String id, EditorialRequest request);

    // Devuelve false si la editorial no existe
    boolean deleteEditorial(String id);
}
