package com.academy.catalog.repo;

import com.academy.catalog.models.Documentation;
import com.academy.catalog.models.TypeDocumentation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentationRepository extends JpaRepository<Documentation, Long> {
    boolean existsByTypeDocumentationAndFilePath(TypeDocumentation typeDocumentation, String filePath);
    List<Documentation> findByTypeDocumentationId(long typeDocumentationId);
    // Метод для получения всех документов, относящихся к CategoryDocumentation
    @Query("SELECT d FROM Documentation d WHERE d.typeDocumentation.categoryDocumentation.id = :categoryId")
    List<Documentation> findByCategoryDocumentationId(@Param("categoryId") long categoryId);
}
