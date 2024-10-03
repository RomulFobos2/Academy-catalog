package com.academy.catalog.service;

import com.academy.catalog.models.CategoryDocumentation;
import com.academy.catalog.models.Documentation;
import com.academy.catalog.models.TypeDocumentation;
import com.academy.catalog.repo.CategoryDocumentationRepository;
import com.academy.catalog.repo.TypeDocumentationRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
@Getter
@Slf4j
public class TypeDocumentationService {
    private final TypeDocumentationRepository typeDocumentationRepository;
    private final CategoryDocumentationRepository categoryDocumentationRepository;
    private final DocumentationService documentationService;

    @Value("${upload.documentation.path}")
    private String uploadDocumentationPath;

    public TypeDocumentationService(TypeDocumentationRepository typeDocumentationRepository,
                                    CategoryDocumentationRepository categoryDocumentationRepository,
                                    DocumentationService documentationService) {
        this.typeDocumentationRepository = typeDocumentationRepository;
        this.categoryDocumentationRepository = categoryDocumentationRepository;
        this.documentationService = documentationService;
    }

    @Transactional
    public boolean saveTypeDocumentation(long categoryDocumentationId, String inputType) {
        log.info("Начинаем сохранять тип = {}...", inputType);

        if (inputType.length() <= 1) {
            log.error("Имя типа должно быть больше одного символа.");
            return false;
        }

        if (inputType.matches(".*[\\\\/:*?\"<>|].*")) {
            log.error("Имя типа содержит недопустимые символы: {}", inputType);
            return false;
        }

        Optional<CategoryDocumentation> categoryDocumentationOptional = categoryDocumentationRepository.findById(categoryDocumentationId);
        if (categoryDocumentationOptional.isEmpty()) {
            log.info("Категория документации по заданному id = \"{}\" не найдена.", categoryDocumentationId);
            return false;
        }
        CategoryDocumentation categoryDocumentation = categoryDocumentationOptional.get();
        if (typeDocumentationRepository.existsByTypeAndCategoryDocumentation(inputType, categoryDocumentationOptional.get())) {
            log.error("Для категории = {} уже существует тип {}.", categoryDocumentationOptional.get().getCategory(), inputType);
            return false;
        }

        TypeDocumentation typeDocumentation = new TypeDocumentation(categoryDocumentationOptional.get(), inputType);

        try {
            typeDocumentationRepository.save(typeDocumentation);
            // Создаем директорию для типа
            Path typeDir = Paths.get(uploadDocumentationPath, categoryDocumentation.getCategory(), typeDocumentation.getType());
            if (!Files.exists(typeDir)) {
                Files.createDirectories(typeDir);
                log.info("Директория для типа '{}' успешно создана по пути '{}'.", typeDocumentation.getType(), typeDir);
            } else {
                log.warn("Директория для типа '{}' уже существует по пути '{}'.", typeDocumentation.getType(), typeDir);
            }
        } catch (Exception e) {
            log.error("Ошибка при сохранении типа {} категории: {}", inputType, categoryDocumentationOptional.get().getCategory(), e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }

        log.info("Тип = {} для категории = {} успешно сохранен.", typeDocumentation.getType(), categoryDocumentationOptional.get().getCategory());

        return true;
    }


    @Transactional
    public boolean editTypeDocumentation(long id, long categoryDocumentationId, String inputType) {

        if (inputType.length() <= 1) {
            log.error("Имя типа должно быть больше одного символа.");
            return false;
        }

        if (inputType.matches(".*[\\\\/:*?\"<>|].*")) {
            log.error("Имя типа содержит недопустимые символы: {}", inputType);
            return false;
        }

        Optional<CategoryDocumentation> categoryDocumentationOptional = categoryDocumentationRepository.findById(categoryDocumentationId);

        if (categoryDocumentationOptional.isEmpty()) {
            log.error("Не найдена категория с id = {}...", id);
            return false;
        }

        Optional<TypeDocumentation> typeDocumentationOptional = typeDocumentationRepository.findById(id);
        if (typeDocumentationOptional.isEmpty()) {
            log.error("Не найден тип с id = {}...", id);
            return false;
        }

        CategoryDocumentation categoryDocumentation = categoryDocumentationOptional.get();
        TypeDocumentation typeDocumentation = typeDocumentationOptional.get();

        // Запоминаем старые значения для перемещения файлов
        String oldTypeName = typeDocumentation.getType();
        String oldCategoryName = typeDocumentation.getCategoryDocumentation().getCategory();

        log.info("Начинаем сохранять изменения для типа = {}...", typeDocumentation.getType());

        if (typeDocumentationRepository.existsByTypeAndCategoryDocumentation(inputType, categoryDocumentation) &&
                (!typeDocumentation.getType().equals(inputType) || !typeDocumentation.getCategoryDocumentation().equals(categoryDocumentation))) {
            log.error("Тип = {} для категории = {} уже существует. Используйте другое название.", inputType, categoryDocumentation.getCategory());
            return false;
        }

        // Обновляем тип и категорию
        typeDocumentation.setType(inputType);
        typeDocumentation.setCategoryDocumentation(categoryDocumentation);

        try {
            // Перемещаем документы, если тип или категория изменились
            if (!oldTypeName.equals(inputType) || !oldCategoryName.equals(categoryDocumentation.getCategory())) {
                boolean moved = documentationService.moveDocumentsToNewDirectory(typeDocumentation, oldCategoryName, oldTypeName);
                if (!moved) {
                    log.error("Не удалось переместить документы. Откат транзакции.");
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return false;
                }
            }

            // Сохраняем изменения типа
            typeDocumentationRepository.save(typeDocumentation);
            // Создаем директорию для нового типа, если она не существует
            Path typeDir = Paths.get(uploadDocumentationPath, categoryDocumentation.getCategory(), inputType);
            if (!Files.exists(typeDir)) {
                Files.createDirectories(typeDir);
                log.info("Директория для типа '{}' успешно создана по пути '{}'.", inputType, typeDir);
            } else {
                log.warn("Директория для типа '{}' уже существует по пути '{}'.", inputType, typeDir);
            }
        } catch (Exception e) {
            log.error("Ошибка при сохранении изменений типа {}, категории {}: {}", typeDocumentation.getType(),
                    typeDocumentation.getCategoryDocumentation().getCategory(), e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }

        log.info("Изменения для типа успешно сохранены. Новое название = {}, новая категория = {}", typeDocumentation.getType(),
                typeDocumentation.getCategoryDocumentation().getCategory());

        return true;
    }


    @Transactional
    public boolean deleteTypeDocumentation(long typeDocumentationId) {

        // Проверяем, существует ли документ с таким ID
        Optional<TypeDocumentation> typeDocumentationOptional = typeDocumentationRepository.findById(typeDocumentationId);

        if (typeDocumentationOptional.isEmpty()) {
            log.error("Тип документации с ID {} не найдена!", typeDocumentationId);
            return false;
        }

        TypeDocumentation typeDocumentation = typeDocumentationOptional.get();
        try {
            List<Documentation> documentationList = documentationService.getDocumentationRepository().findByTypeDocumentationId(typeDocumentationId);
            for (Documentation document : documentationList) {
                boolean isDeleted = documentationService.deleteDocumentation(document.getId());
                if (!isDeleted) {
                    log.error("Не удалось удалить документ {} с id = {}. Откат транзакции.", document.getFilePath(), document.getId());
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return false;
                }
            }

            typeDocumentationRepository.deleteById(typeDocumentationId);

            Path typeDirectoryPath = Paths.get(uploadDocumentationPath, typeDocumentation.getType());
            FileService.deleteDirectoryRecursively(typeDirectoryPath);

            log.info("Тип документации {} и связанные с ним документы удалены.", typeDocumentation.getType());

            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении типа документации с ID {}. Причина: {}", typeDocumentationId, e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    public List<TypeDocumentation> getCategoryTypes(CategoryDocumentation categoryDocumentation){
        return typeDocumentationRepository.findByCategoryDocumentation(categoryDocumentation);
    }
}
