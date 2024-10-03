package com.academy.catalog.repo;

import com.academy.catalog.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByRoleName(String roleName);
    List<User> findAllByRoleName(String roleName);
    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.patronymicName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "CAST(u.course AS string) LIKE CONCAT('%', :searchTerm, '%') OR " +
            "LOWER(u.faculty) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "CAST(u.dateOfRegistration AS string) LIKE CONCAT('%', :searchTerm, '%')")
    Page<User> searchUsersByMultipleFields(@Param("searchTerm") String searchTerm, Pageable pageable);

    // 1. Поиск по фамилии (LIKE)
    @Query("SELECT u FROM User u WHERE LOWER(u.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))")
    Page<User> findByLastName(@Param("lastName") String lastName, Pageable pageable);

    // 2. Поиск по имени (LIKE)
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :firstName, '%'))")
    Page<User> findByFirstName(@Param("firstName") String firstName, Pageable pageable);

    // 3. Поиск по отчеству (LIKE)
    @Query("SELECT u FROM User u WHERE LOWER(u.patronymicName) LIKE LOWER(CONCAT('%', :patronymicName, '%'))")
    Page<User> findByPatronymicName(@Param("patronymicName") String patronymicName, Pageable pageable);

    // 4. Поиск по логину (LIKE)
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))")
    Page<User> findByUsernameContaining(@Param("username") String username, Pageable pageable);

    // 5. Поиск по курсу (если требуется точное совпадение)
    Page<User> findByCourse(int course, Pageable pageable);

    // 6. Поиск по факультету (LIKE)
    @Query("SELECT u FROM User u WHERE LOWER(u.faculty) LIKE LOWER(CONCAT('%', :faculty, '%'))")
    Page<User> findByFaculty(@Param("faculty") String faculty, Pageable pageable);

    // 7. Поиск по диапазону дат регистрации
    @Query("SELECT u FROM User u WHERE u.dateOfRegistration BETWEEN :startDate AND :endDate")
    Page<User> findByDateOfRegistrationBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, Pageable pageable);

}
