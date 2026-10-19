package com.noSQL.claseJava.service;

import com.noSQL.claseJava.dto.UserRequest;
import com.noSQL.claseJava.entity.User;

import java.util.List;
import java.util.Optional;

// Operaciones de los usuarios. La implementación está en service/impl/UserServiceImpl
public interface UserService {

    // Crea el usuario. El email no se puede repetir
    User createUser(UserRequest request);

    List<User> getAllUsers();

    Optional<User> getUserById(String id);

    Optional<User> getUserByEmail(String email);

    // Usuarios cuyo nombre contiene el texto (sin importar mayúsculas)
    List<User> searchUsersByName(String name);

    List<User> getUsersWithActiveLoans();

    // Resumen de actividad: contadores y últimos préstamos
    Optional<User.ActivitySummary> getUserActivity(String id);

    // Edita el usuario y copia el cambio a préstamos, reseñas y favoritos. Devuelve null si no existe
    User updateUser(String id, UserRequest request);

    // Devuelve false si el usuario no existe. No se puede borrar un usuario con préstamos activos
    boolean deleteUser(String id);
}
