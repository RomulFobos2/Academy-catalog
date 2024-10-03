package com.academy.catalog.service;

import com.academy.catalog.models.CategoryDocumentation;
import com.academy.catalog.models.TypeDocumentation;
import com.academy.catalog.repo.CategoryDocumentationRepository;
import com.academy.catalog.repo.TypeDocumentationRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
@Getter
@Slf4j
public class CategoryDocumentationService {
    private final CategoryDocumentationRepository categoryDocumentationRepository;
    private final TypeDocumentationRepository typeDocumentationRepository;
    private final TypeDocumentationService typeDocumentationService;
    private final DocumentationService documentationService;

    @Value("${upload.documentation.path}")
    private String uploadDocumentationPath;

    public CategoryDocumentationService(CategoryDocumentationRepository categoryDocumentationRepository,
                                        TypeDocumentationRepository typeDocumentationRepository,
                                        TypeDocumentationService typeDocumentationService,
                                        DocumentationService documentationService) {
        this.categoryDocumentationRepository = categoryDocumentationRepository;
        this.typeDocumentationRepository = typeDocumentationRepository;
        this.typeDocumentationService = typeDocumentationService;
        this.documentationService = documentationService;
    }

    @Transactional
    public boolean saveCategoryDocumentation(CategoryDocumentation categoryDocumentation) {
        log.info("Начинаем сохранять категорию = {}...", categoryDocumentation.getCategory());

        if(categoryDocumentation.getCategory().length() <= 1){
            log.error("Имя категории должно быть больше одного символа.");
            return false;
        }

        if (categoryDocumentation.getCategory().matches(".*[\\\\/:*?\"<>|].*")) {
            log.error("Имя категории содержит недопустимые символы: {}", categoryDocumentation.getCategory());
            return false;
        }

        if (categoryDocumentationRepository.existsByCategory(categoryDocumentation.getCategory())) {
            log.error("Категория = {} уже существует.", categoryDocumentation.getCategory());
            return false;
        }

        try {
            categoryDocumentationRepository.save(categoryDocumentation);
            // Создаем директорию для категории
            Path categoryDir = Paths.get(uploadDocumentationPath, categoryDocumentation.getCategory());
            if (!Files.exists(categoryDir)) {
                Files.createDirectories(categoryDir);
                log.info("Директория для категории '{}' успешно создана по пути '{}'.", categoryDocumentation.getCategory(), categoryDir);
            } else {
                log.warn("Директория для категории '{}' уже существует по пути '{}'.", categoryDocumentation.getCategory(), categoryDir);
            }
        } catch (Exception e) {
            log.error("Ошибка при сохранении категории {}: {}", categoryDocumentation.getCategory(), e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
        log.info("Категория = '{}' успешно сохранена в базе данных.", categoryDocumentation.getCategory());
        return true;
    }


    @Transactional
    public boolean editCategoryDocumentation(long id, String inputCategory) {

        if (inputCategory.length() <= 1) {
            log.error("Имя категории должно быть больше одного символа.");
            return false;
        }

        if (inputCategory.matches(".*[\\\\/:*?\"<>|].*")) {
            log.error("Имя категории содержит недопустимые символы: {}", inputCategory);
            return false;
        }

        Optional<CategoryDocumentation> categoryDocumentationOptional = categoryDocumentationRepository.findById(id);

        if (categoryDocumentationOptional.isEmpty()) {
            log.error("Не найдена категория с id = {}...", id);
            return false;
        }

        CategoryDocumentation categoryDocumentation = categoryDocumentationOptional.get();

        // Запоминаем старое имя категории для перемещения файлов
        String oldCategoryName = categoryDocumentation.getCategory();

        log.info("Начинаем сохранять изменения для категории = {}...", categoryDocumentation.getCategory());

        // Проверяем, существует ли уже категория с таким названием
        if (categoryDocumentationRepository.existsByCategory(inputCategory) &&
                !categoryDocumentation.getCategory().equals(inputCategory)) {
            log.error("Категория = {} уже существует. Используйте другое название.", inputCategory);
            return false;
        }

        // Обновляем имя категории
        categoryDocumentation.setCategory(inputCategory);

        try {
            // Перемещаем документы, если изменилось имя категории
            if (!oldCategoryName.equals(inputCategory)) {
                boolean moved = documentationService.moveCategoryDocumentsToNewDirectory(categoryDocumentation, oldCategoryName);
                if (!moved) {
                    log.error("Не удалось переместить документы для категории {}. Откат транзакции.", oldCategoryName);
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return false;
                }
            }

            // Сохраняем изменения в базе данных
            categoryDocumentationRepository.save(categoryDocumentation);
            // Создаём директорию для новой категории, если она не существует
            Path categoryDir = Paths.get(uploadDocumentationPath, inputCategory);
            if (!Files.exists(categoryDir)) {
                Files.createDirectories(categoryDir);
                log.info("Директория для категории '{}' успешно создана по пути '{}'.", inputCategory, categoryDir);
            } else {
                log.warn("Директория для категории '{}' уже существует по пути '{}'.", inputCategory, categoryDir);
            }
        } catch (Exception e) {
            log.error("Ошибка при сохранении изменений категории {}: {}", categoryDocumentation.getCategory(), e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }

        log.info("Изменения для категории успешно сохранены. Новое название = {}", categoryDocumentation.getCategory());

        return true;
    }

    @Transactional
    public boolean deleteCategoryDocumentation(long categoryDocumentationId) {
        Optional<CategoryDocumentation> categoryDocumentationOptional = categoryDocumentationRepository.findById(categoryDocumentationId);

        if (categoryDocumentationOptional.isEmpty()) {
            log.error("Категория с id = {} не найдена.", categoryDocumentationId);
            return false;
        }

        CategoryDocumentation categoryDocumentation = categoryDocumentationOptional.get();

        try {
            List<TypeDocumentation> types = typeDocumentationRepository.findByCategoryDocumentation(categoryDocumentation);
            for (TypeDocumentation type : types) {
                    boolean isDeleted = typeDocumentationService.deleteTypeDocumentation(type.getId());
                    if (!isDeleted) {
                        log.error("Не удалось удалить тип документации {} с id = {}. Откат транзакции.", type.getType(), type.getId());
                        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                        return false;
                    }
            }
            categoryDocumentationRepository.deleteById(categoryDocumentationId);

            Path categoryDirectoryPath = Paths.get(uploadDocumentationPath, categoryDocumentation.getCategory());
            FileService.deleteDirectoryRecursively(categoryDirectoryPath);

            log.info("Категория {} и связанные с ней типы и документы успешно удалены.", categoryDocumentation.getCategory());

        } catch (Exception e) {
            log.error("Ошибка при удалении категории с id = {}: {}", categoryDocumentationId, e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }

        return true;
    }
}