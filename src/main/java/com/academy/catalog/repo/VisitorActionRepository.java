package com.academy.catalog.repo;

import com.academy.catalog.models.VisitorAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VisitorActionRepository extends JpaRepository<VisitorAction, Long> {
    List<VisitorAction> findByUsernameAndVisitorFullName(String username, String visitorFullName);
    Page<VisitorAction> findByUsernameAndVisitorFullNameOrderByTimeOfVisitDesc(String username, String visitorFullName, Pageable pageable);
    Page<VisitorAction> findAll(Pageable pageable);

    // Метод для фильтрации по visitorFullName с использованием LIKE и диапазоном дат
    @Query("SELECT v FROM VisitorAction v WHERE v.visitorFullName LIKE %:visitorFullName% AND v.timeOfVisit BETWEEN :from AND :to")
    Page<VisitorAction> findByVisitorFullNameAndTimeOfVisitBetween(
            @Param("visitorFullName") String visitorFullName,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    // Метод для фильтрации по username с использованием LIKE и диапазоном дат
    @Query("SELECT v FROM VisitorAction v WHERE v.username LIKE %:username% AND v.timeOfVisit BETWEEN :from AND :to")
    Page<VisitorAction> findByUsernameAndTimeOfVisitBetween(
            @Param("username") String username,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    // Метод для фильтрации по documentPath с использованием LIKE и диапазоном дат
    @Query("SELECT v FROM VisitorAction v WHERE v.documentPath LIKE %:documentPath% AND v.timeOfVisit BETWEEN :from AND :to")
    Page<VisitorAction> findByDocumentPathAndTimeOfVisitBetween(
            @Param("documentPath") String documentPath,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    // Метод для поиска по строке input и диапазону дат
    @Query("SELECT v FROM VisitorAction v WHERE (v.username LIKE %:input% OR v.visitorFullName LIKE %:input% OR v.documentPath LIKE %:input%) AND v.timeOfVisit BETWEEN :from AND :to")
    Page<VisitorAction> searchByInputAndTimeOfVisitBetween(
            @Param("input") String input,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    // Метод для группировки по имени документа и подсчета количества открытий за указанный период
    @Query("SELECT v.documentPath, COUNT(v) as openCount FROM VisitorAction v WHERE v.timeOfVisit BETWEEN :from AND :to GROUP BY v.documentPath")
    List<Object[]> findDocumentOpenCountsByTimeOfVisitBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

}

