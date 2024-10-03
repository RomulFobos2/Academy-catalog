package com.academy.catalog.component;

import com.academy.catalog.models.CategoryDocumentation;
import com.academy.catalog.models.TypeDocumentation;
import com.academy.catalog.service.CategoryDocumentationService;
import com.academy.catalog.service.DocumentationService;
import com.academy.catalog.service.FileService;
import com.academy.catalog.service.TypeDocumentationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@Slf4j
public class ScheduleTask {
    private final CategoryDocumentationService categoryDocumentationService;
    private final TypeDocumentationService typeDocumentationService;
    private final DocumentationService documentationService;

    @Value("${upload.documentation.path}")
    private String uploadDocumentationPath;

    public ScheduleTask(CategoryDocumentationService categoryDocumentationService,
                        TypeDocumentationService typeDocumentationService,
                        DocumentationService documentationService) {
        this.categoryDocumentationService = categoryDocumentationService;
        this.typeDocumentationService = typeDocumentationService;
        this.documentationService = documentationService;
    }

    @Bean
    public ApplicationRunner applicationRunner() {
        return args -> {
            cleanUpDirectories();
        };

    }

    // Запускаем метод при старте программы и затем каждый день в полночь
    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanUpDirectories() {
        try {
            // Сначала проверяем категории
            checkCategoryDirectories();

            // Затем проверяем типы внутри существующих категорий
            checkTypeDirectories();
        } catch (IOException e) {
            log.error("Ошибка при очистке директорий: ", e);
        }
    }

    // Метод для проверки папок категорий
    private void checkCategoryDirectories() throws IOException {
        log.info("Начинаем проверку категорий...");
        try (Stream<Path> categories = Files.list(Paths.get(uploadDocumentationPath))) {
            categories.filter(Files::isDirectory).forEach(categoryPath -> {
                String categoryName = categoryPath.getFileName().toString();

                // Проверяем, существует ли категория в базе данных
                Optional<CategoryDocumentation> categoryOpt = categoryDocumentationService.getCategoryDocumentationRepository().findByCategory(categoryName);
                if (categoryOpt.isEmpty()) {
                    // Категории нет в базе, удаляем всю папку категории с её содержимым
                    log.info("Категория '{}' не найдена в базе данных. Удаляем директорию '{}'.", categoryName, categoryPath);
                    try {
                        FileService.deleteDirectoryRecursively(categoryPath);
                        log.info("Удалена директория категории '{}'", categoryPath);
                    } catch (IOException e) {
                        log.error("Ошибка при удалении папки категории '{}'", categoryPath, e);
                    }
                }
            });
        }
    }

    // Метод для проверки папок типов документации
    private void checkTypeDirectories() throws IOException {
        log.info("Начинаем проверку типов документации...");
        try (Stream<Path> categories = Files.list(Paths.get(uploadDocumentationPath))) {
            categories.filter(Files::isDirectory).forEach(categoryPath -> {
                String categoryName = categoryPath.getFileName().toString();
                log.info("Проверяем категорию '{}'", categoryName);

                // Проверяем каждый тип в категории
                try (Stream<Path> types = Files.list(categoryPath)) {
                    types.filter(Files::isDirectory).forEach(typePath -> {
                        String typeName = typePath.getFileName().toString();
                        log.info("Проверяем тип '{}'", typeName);

                        // Проверяем, существует ли категория в базе данных
                        Optional<CategoryDocumentation> categoryOpt = categoryDocumentationService.getCategoryDocumentationRepository().findByCategory(categoryName);
                        if (categoryOpt.isPresent()) {
                            Optional<TypeDocumentation> typeOpt = typeDocumentationService.getTypeDocumentationRepository().findByCategoryDocumentationAndType(categoryOpt.get(), typeName);

                            if (typeOpt.isEmpty()) {
                                // Типа нет в базе, удаляем папку типа
                                log.info("Тип '{}' не найден в базе данных. Удаляем директорию '{}'.", typeName, typePath);
                                try {
                                    FileService.deleteDirectoryRecursively(typePath);
                                    log.info("Удалена директория типа '{}'", typePath);
                                } catch (IOException e) {
                                    log.error("Ошибка при удалении папки типа '{}'", typePath, e);
                                }
                            }
                        } else {
                            log.warn("Категория '{}' не найдена в базе данных.", categoryName);
                        }
                    });
                } catch (IOException e) {
                    log.error("Ошибка при работе с типами в категории '{}'.", categoryPath.toString(), e);
                }
            });
        }
    }


    // Метод для удаления папки, если она пуста
    private void deleteDirectoryIfEmpty(Path directory) {
        try {
            if (Files.list(directory).findAny().isEmpty()) {
                Files.delete(directory);
                log.info("Удалена пустая папка: {}", directory);
            } else {
                log.info("Папка '{}' не пуста, удаление пропущено.", directory);
            }
        } catch (IOException e) {
            log.error("Ошибка при попытке удалить папку '{}'.", directory, e);
        }
    }
}