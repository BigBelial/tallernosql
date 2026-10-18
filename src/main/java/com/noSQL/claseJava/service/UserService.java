package com.noSQL.claseJava.service;

import com.noSQL.claseJava.config.CacheNames;
import com.noSQL.claseJava.dto.UserRequest;
import com.noSQL.claseJava.entity.LoanStatus;
import com.noSQL.claseJava.entity.User;
import com.noSQL.claseJava.exception.BusinessException;
import com.noSQL.claseJava.repository.LoanRepository;
import com.noSQL.claseJava.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final LoanRepository loanRepository;
    private final DenormalizationService denormalizationService;
    private final MongoTemplate mongoTemplate;

    @CacheEvict(cacheNames = CacheNames.USERS, allEntries = true)
    public User createUser(UserRequest request) {
        String email = normalizeEmail(request.getEmail());
        // Regla de negocio: no pueden existir dos usuarios con el mismo email
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("A user with email '" + email + "' already exists");
        }
        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .phone(request.getPhone())
                .address(request.getAddress())
                .createdAt(LocalDateTime.now())
                .activity(User.ActivitySummary.builder().build())
                .build();
        return userRepository.save(user);
    }

    @Cacheable(CacheNames.USERS)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Cacheable(cacheNames = CacheNames.USERS, unless = "#result == null")
    public Optional<User> getUserById(String id) {
        return userRepository.findById(id);
    }

    @Cacheable(cacheNames = CacheNames.USERS, unless = "#result == null")
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email));
    }

    @Cacheable(CacheNames.USERS)
    public List<User> searchUsersByName(String name) {
        return userRepository.findByFullNameContainingIgnoreCase(name);
    }

    @Cacheable(CacheNames.USERS)
    public List<User> getUsersWithActiveLoans() {
        return userRepository.findUsersWithActiveLoans();
    }

    // El resumen de actividad (contadores y últimos préstamos) ya está guardado dentro del usuario,
    // por eso sale con una sola consulta
    @Cacheable(cacheNames = CacheNames.USERS, unless = "#result == null")
    public Optional<User.ActivitySummary> getUserActivity(String id) {
        return userRepository.findById(id).map(User::getActivity);
    }

    // Al cambiar el nombre o el email hay que actualizar todas las copias, por eso se limpian varias cachés
    @CacheEvict(cacheNames = {CacheNames.USERS, CacheNames.LOANS, CacheNames.REVIEWS, CacheNames.FAVORITES,
            CacheNames.BOOKS, CacheNames.BOOK_DETAILS, CacheNames.BOOK_QUERIES, CacheNames.LOAN_STATS}, allEntries = true)
    public User updateUser(String id, UserRequest request) {
        Optional<User> existingUser = userRepository.findById(id);
        if (existingUser.isEmpty()) {
            return null;
        }
        User user = existingUser.get();
        String fullName = request.getFullName().trim();
        String email = normalizeEmail(request.getEmail());

        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new BusinessException("A user with email '" + email + "' already exists");
        }
        boolean copiedDataChanged = !Objects.equals(user.getFullName(), fullName)
                || !Objects.equals(user.getEmail(), email);

        // Se usa $set solo con los datos personales, así no se pisan los contadores de actividad
        // que cambian los préstamos y las reseñas
        Update update = new Update()
                .set("fullName", fullName)
                .set("email", email)
                .set("phone", request.getPhone())
                .set("address", request.getAddress());
        User updated = mongoTemplate.findAndModify(query(where("id").is(id)), update,
                FindAndModifyOptions.options().returnNew(true), User.class);
        if (updated == null) {
            return null;
        }

        if (copiedDataChanged) {
            denormalizationService.propagateUserChanges(updated);
        }
        return updated;
    }

    @CacheEvict(cacheNames = {CacheNames.USERS, CacheNames.REVIEWS, CacheNames.FAVORITES,
            CacheNames.BOOKS, CacheNames.BOOK_DETAILS, CacheNames.BOOK_QUERIES}, allEntries = true)
    public boolean deleteUser(String id) {
        if (!userRepository.existsById(id)) {
            return false;
        }
        // Regla de negocio: un usuario con libros prestados no se puede borrar
        if (loanRepository.countByUserIdAndStatus(id, LoanStatus.ACTIVE) > 0) {
            throw new BusinessException("User has active loans and cannot be deleted");
        }
        denormalizationService.removeUserReferences(id);
        userRepository.deleteById(id);
        return true;
    }

    // Los emails se guardan en minúscula y sin espacios para que "Ana@Mail.com" y "ana@mail.com" sean el mismo
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
