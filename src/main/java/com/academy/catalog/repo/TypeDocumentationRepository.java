package com.academy.catalog.repo;

import com.academy.catalog.models.CategoryDocumentation;
import com.academy.catalog.models.TypeDocumentation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TypeDocumentationRepository extends JpaRepository<TypeDocumentation, Long> {
    boolean existsByTypeAndCategoryDocumentation(String type, CategoryDocumentation categoryDocumentation);
    List<TypeDocumentation> findByCategoryDocumentation(CategoryDocumentation categoryDocumentation);
    Optional<TypeDocumentation> findByCategoryDocumentationAndType(CategoryDocumentation categoryDocumentation, String type);
}
