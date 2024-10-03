package com.academy.catalog.repo;

import com.academy.catalog.models.CategoryDocumentation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryDocumentationRepository extends JpaRepository<CategoryDocumentation, Long>{
    boolean existsByCategory(String category);
    Optional<CategoryDocumentation> findByCategory(String category);
}
