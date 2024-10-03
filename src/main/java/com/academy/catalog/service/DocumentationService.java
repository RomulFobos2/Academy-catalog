package com.academy.catalog.service;

import com.academy.catalog.models.CategoryDocumentation;
import com.academy.catalog.models.Documentation;
import com.academy.catalog.models.TypeDocumentation;
import com.academy.catalog.repo.CategoryDocumentationRepository;
import com.academy.catalog.repo.DocumentationRepository;
import com.academy.catalog.repo.TypeDocumentationRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Getter
@Slf4j
public class DocumentationService {
    private final TypeDocumentationRepository typeDocumentationRepository;
    private final CategoryDocumentationRepository categoryDocumentationRepository;
    private final DocumentationRepository documentationRepository;

    @Value("${upload.documentation.path}")
    private String uploadDocumentationPath;

    public DocumentationService(TypeDocumentationRepository typeDocumentationRepository,
                                CategoryDocumentationRepository categoryDocumentationRepository,
                                DocumentationRepository documentationRepository) {
        this.typeDocumentationRepository = typeDocumentationRepository;
        this.categoryDocumentationRepository = categoryDocumentationRepository;
        this.documentationRepository = documentationRepository;
    }


    @Transactional
    public boolean saveDocumentation(long typeDocumentationId, MultipartFile inputFileField) {
        log.info("Начинаем сохранять документ = {}...", inputFileField.getOriginalFilename());

        Optional<TypeDocumentation> typeDocumentationOptional = typeDocumentationRepository.findById(typeDocumentationId);
        if (typeDocumentationOptional.isEmpty()) {
            log.info("Тип документации по заданному id = \"{}\" не найден.", typeDocumentationId);
            return false;
        }

        TypeDocumentation typeDocumentation = typeDocumentationOptional.get();

        // Формирование пути до файла с использованием Paths для избежания ошибок форматирования
        String categoryPath = typeDocumentation.getCategoryDocumentation().getCategory();
        String typePath = typeDocumentation.getType();
        String fileName = inputFileField.getOriginalFilename();
        String filePath = Paths.get(categoryPath, typePath, fileName).toString();

        // Проверка на существование документа с таким же именем
        if (documentationRepository.existsByTypeDocumentationAndFilePath(typeDocumentation, filePath)) {
            log.warn("Для категории {} и типа = {} уже существует документ с таким именем {}.",
                    categoryPath, typePath, fileName);
            return false;
        }

        Documentation documentation = new Documentation(typeDocumentation, filePath);

        try {
            uploadDocumentation(inputFileField, Paths.get(categoryPath, typePath).toString());
            documentationRepository.save(documentation);
        } catch (Exception e) {
            log.error("Ошибка при сохранении документа {} для категории {} и типа {}. Причина: {}",
                    fileName, categoryPath, typePath, e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }

        log.info("Документ = {} для категории = {} и типа {} успешно сохранен.",
                fileName, categoryPath, typePath);
        return true;
    }


    @Transactional
    public boolean editDocumentation(long id, long typeDocumentationId, MultipartFile inputFileField) {
        Optional<TypeDocumentation> typeDocumentationOptional = typeDocumentationRepository.findById(typeDocumentationId);
        if (typeDocumentationOptional.isEmpty()) {
            log.error("Не найден тип документации с id = {}...", typeDocumentationId);
            return false;
        }

        Optional<Documentation> documentationOptional = documentationRepository.findById(id);
        if (documentationOptional.isEmpty()) {
            log.error("Не найдена документация с id = {}...", id);
            return false;
        }

        TypeDocumentation newTypeDocumentation = typeDocumentationOptional.get();
        CategoryDocumentation newCategoryDocumentation = newTypeDocumentation.getCategoryDocumentation();
        Documentation documentation = documentationOptional.get();

        TypeDocumentation oldTypeDocumentation = documentation.getTypeDocumentation();

        log.info("Начинаем редактировать документацию с id = {}...", id);

        String oldFilePath = documentation.getFilePath();
        String newFilePath = oldFilePath;

        if (inputFileField != null && !inputFileField.isEmpty()) {
            // Если загружен новый файл
            newFilePath = Paths.get(newCategoryDocumentation.getCategory(), newTypeDocumentation.getType(), inputFileField.getOriginalFilename()).toString();

            if (documentationRepository.existsByTypeDocumentationAndFilePath(newTypeDocumentation, newFilePath) &&
                    (!oldFilePath.equals(newFilePath) || !documentation.getTypeDocumentation().equals(newTypeDocumentation))) {
                log.error("Документ с именем {} для категории {} и типа {} уже существует. Используйте другой файл.",
                        inputFileField.getOriginalFilename(), newCategoryDocumentation.getCategory(), newTypeDocumentation.getType());
                return false;
            }
        } else {
            // Если новый файл не загружен, но изменились категория или тип
            if (!oldTypeDocumentation.equals(newTypeDocumentation)) {
                String fileName = Paths.get(oldFilePath).getFileName().toString();
                newFilePath = Paths.get(newCategoryDocumentation.getCategory(), newTypeDocumentation.getType(), fileName).toString();

                if (documentationRepository.existsByTypeDocumentationAndFilePath(newTypeDocumentation, newFilePath)) {
                    log.error("Документ с именем {} для категории {} и типа {} уже существует. Используйте другой файл.",
                            fileName, newCategoryDocumentation.getCategory(), newTypeDocumentation.getType());
                    return false;
                }
            }
        }

        try {
            if (inputFileField != null && !inputFileField.isEmpty()) {
                // Загружаем новый файл
                uploadDocumentation(inputFileField, Paths.get(newCategoryDocumentation.getCategory(), newTypeDocumentation.getType()).toString());

                if (!oldFilePath.equals(newFilePath)) {
                    Path oldPath = Paths.get(uploadDocumentationPath, oldFilePath);
                    Files.deleteIfExists(oldPath);
                    log.info("Старый файл по пути {} был успешно удален.", oldPath);
                }
                documentation.setFilePath(newFilePath);
            } else {
                // Новый файл не загружен, но изменились категория или тип
                if (!oldTypeDocumentation.equals(newTypeDocumentation)) {
                    Path oldFile = Paths.get(uploadDocumentationPath, oldFilePath);
                    Path newFile = Paths.get(uploadDocumentationPath, newFilePath);

                    // Создаем директории, если они не существуют
                    Files.createDirectories(newFile.getParent());

                    // Перемещаем файл
                    Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Файл был перемещен из {} в {}", oldFile, newFile);

                    // Обновляем путь к файлу в сущности
                    documentation.setFilePath(newFilePath);
                }
            }

            documentation.setTypeDocumentation(newTypeDocumentation);
            documentationRepository.save(documentation);
        } catch (Exception e) {
            log.error("Ошибка при редактировании документа с id = {}. Причина: {}", id, e.getMessage(), e);

            // В случае ошибки пытаемся откатить изменения
            if (inputFileField != null && !inputFileField.isEmpty()) {
                try {
                    Path newPath = Paths.get(uploadDocumentationPath, newFilePath);
                    Files.deleteIfExists(newPath);
                    log.info("Новый файл по пути {} был удален из-за ошибки.", newPath);
                } catch (IOException ex) {
                    log.error("Не удалось удалить новый файл по пути {} после ошибки редактирования. Причина: {}", newFilePath, ex.getMessage(), ex);
                }
            } else {
                if (!oldTypeDocumentation.equals(newTypeDocumentation)) {
                    // Пытаемся вернуть файл в старую директорию
                    try {
                        Path oldFile = Paths.get(uploadDocumentationPath, oldFilePath);
                        Path newFile = Paths.get(uploadDocumentationPath, newFilePath);
                        Files.move(newFile, oldFile, StandardCopyOption.REPLACE_EXISTING);
                        log.info("Файл был перемещен обратно из {} в {}", newFile, oldFile);
                    } catch (IOException ex) {
                        log.error("Не удалось переместить файл обратно после ошибки редактирования. Причина: {}", ex.getMessage(), ex);
                    }
                }
            }
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }

        log.info("Документ с id = {} успешно отредактирован. Категория = {}, Тип = {}, Путь к файлу = {}",
                id, newCategoryDocumentation.getCategory(), newTypeDocumentation.getType(), newFilePath);
        return true;
    }

    private String uploadDocumentation(MultipartFile file, String pathForType) throws IOException {
        if (file == null || file.isEmpty()) {
            String errorMessage = "Ошибка: файл не предоставлен или пустой.";
            log.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        try {
            String filePath = pathForType + "/" + file.getOriginalFilename();
            Path folderPath = Paths.get(uploadDocumentationPath).resolve(filePath);

            // Создаем директории, если они не существуют
            Files.createDirectories(folderPath.getParent());
            // Копируем файл
            Files.copy(file.getInputStream(), folderPath, StandardCopyOption.REPLACE_EXISTING);

            return filePath;
        } catch (IOException e) {
            String errorMessage = "Ошибка при загрузке файла: " + file.getOriginalFilename() + " в директорию: " + pathForType;
            log.error(errorMessage, e);
            throw new IOException(errorMessage, e);  // Передаем исключение дальше с логированием
        }
    }

    @Transactional
    public boolean deleteDocumentation(long documentationId) {
        try {
            // Проверяем, существует ли документ с таким ID
            Optional<Documentation> documentationOptional = documentationRepository.findById(documentationId);

            if (documentationOptional.isEmpty()) {
                log.error("Документация с ID {} не найдена!", documentationId);
                return false;
            }

            // Удаляем документ
            Documentation documentation = documentationOptional.get();
            documentationRepository.delete(documentation);

            // Удаляем файл с файловой системы
            Path filePath = Paths.get(uploadDocumentationPath).resolve(documentation.getFilePath());
            File fileToDelete = filePath.toFile();
            if (fileToDelete.exists()) {
                if (fileToDelete.delete()) {
                    log.info("Файл {} был успешно удален.", documentation.getFilePath());
                } else {
                    log.error("Не удалось удалить файл {}", documentation.getFilePath());
                }
            }

            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении документации с ID {}. Причина: {}", documentationId, e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    public boolean moveDocumentsToNewDirectory(TypeDocumentation typeDocumentation, String oldCategoryName, String oldTypeName) {
        try {
            // Получаем список всех документов, связанных с данным типом
            List<Documentation> documents = documentationRepository.findByTypeDocumentationId(typeDocumentation.getId());

            String newCategoryName = typeDocumentation.getCategoryDocumentation().getCategory();
            String newTypeName = typeDocumentation.getType();

            for (Documentation document : documents) {
                // Текущий путь файла
                Path oldFilePath = Paths.get(uploadDocumentationPath, document.getFilePath());

                // Новый путь файла с обновленным именем категории и типа
                Path newFilePath = Paths.get(uploadDocumentationPath, newCategoryName, newTypeName, oldFilePath.getFileName().toString());

                // Создаем директории для нового пути, если они не существуют
                Files.createDirectories(newFilePath.getParent());

                // Перемещаем файл в новую директорию
                Files.move(oldFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);

                // Обновляем путь в сущности документа
                document.setFilePath(Paths.get(newCategoryName, newTypeName, oldFilePath.getFileName().toString()).toString());

                // Сохраняем обновления в базе данных
                documentationRepository.save(document);
            }

            // Если все файлы успешно перемещены, возвращаем true
            return true;
        } catch (IOException e) {
            log.error("Ошибка при перемещении файлов для типа {}, категория {}: {}", oldTypeName, oldCategoryName, e.getMessage());
            return false;
        }
    }

    public boolean moveCategoryDocumentsToNewDirectory(CategoryDocumentation categoryDocumentation, String oldCategoryName) {
        try {
            // Получаем список всех типов, связанных с данной категорией
            List<TypeDocumentation> types = typeDocumentationRepository.findByCategoryDocumentation(categoryDocumentation);

            String newCategoryName = categoryDocumentation.getCategory();

            for (TypeDocumentation type : types) {
                // Получаем список всех документов, связанных с типом
                List<Documentation> documents = documentationRepository.findByTypeDocumentationId(type.getId());

                for (Documentation document : documents) {
                    // Текущий путь файла
                    Path oldFilePath = Paths.get(uploadDocumentationPath, document.getFilePath());

                    // Новый путь файла с обновленным именем категории
                    Path newFilePath = Paths.get(uploadDocumentationPath, newCategoryName, type.getType(), oldFilePath.getFileName().toString());

                    // Создаем директории для нового пути, если они не существуют
                    Files.createDirectories(newFilePath.getParent());

                    // Перемещаем файл в новую директорию
                    Files.move(oldFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);

                    // Обновляем путь в сущности документа
                    document.setFilePath(Paths.get(newCategoryName, type.getType(), oldFilePath.getFileName().toString()).toString());

                    // Сохраняем обновления в базе данных
                    documentationRepository.save(document);
                }
            }

            // Если все файлы успешно перемещены, возвращаем true
            return true;
        } catch (IOException e) {
            log.error("Ошибка при перемещении файлов для категории {}: {}", oldCategoryName, e.getMessage());
            return false;
        }
    }


    public List<Documentation> getAllDocumentsByCategoryId(CategoryDocumentation categoryDocumentation){
        return documentationRepository.findByCategoryDocumentationId(categoryDocumentation.getId());
    }
}
