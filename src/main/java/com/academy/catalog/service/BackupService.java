package com.academy.catalog.service;

import com.academy.catalog.models.Backup;
import com.academy.catalog.models.CategoryDocumentation;
import com.academy.catalog.models.Documentation;
import com.academy.catalog.models.TypeDocumentation;
import com.academy.catalog.repo.BackupRepository;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
@Getter
@Slf4j
public class BackupService {
    private final BackupRepository backupRepository;
    private final CategoryDocumentationService categoryDocumentationService;
    private final TypeDocumentationService typeDocumentationService;
    private final DocumentationService documentationService;

    @Value("${store.backup.path}")
    private String uploadBackupPath;

    @Value("${upload.documentation.path}")
    private String uploadDocumentationPath;

    public BackupService(BackupRepository backupRepository, CategoryDocumentationService categoryDocumentationService, TypeDocumentationService typeDocumentationService, DocumentationService documentationService) {
        this.backupRepository = backupRepository;
        this.categoryDocumentationService = categoryDocumentationService;
        this.typeDocumentationService = typeDocumentationService;
        this.documentationService = documentationService;
    }

    // Метод для создания резервной копии (бэкапа) директории и возврата имени архива и его контрольной суммы
    public Map<String, String> createBackup() {
        // Формируем имя для файла бэкапа, включающее текущую дату и время
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String backupFileName = "backup_" + timestamp + ".zip";

        // Путь к временному архиву и финальному архиву
        Path temporaryBackupFilePath = Paths.get(uploadBackupPath, "tmp_" + backupFileName);
        Path finalBackupFilePath = Paths.get(uploadBackupPath, backupFileName);

        // Инициализация мапы для хранения имени архива и его контрольной суммы
        Map<String, String> result = new HashMap<>();

        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(temporaryBackupFilePath.toFile()))) {
            Path sourceDirPath = Paths.get(uploadDocumentationPath);

            log.info("Начинаем добавление файлов в архив...");

            // Рекурсивно добавляем файлы в ZIP-архив
            Files.walk(sourceDirPath).forEach(path -> {
                try {
                    String zipEntryName = sourceDirPath.relativize(path).toString();

                    if (Files.isDirectory(path)) {
                        if (!zipEntryName.isEmpty()) {
                            zipOut.putNextEntry(new ZipEntry(zipEntryName + "/"));
                            zipOut.closeEntry();
                        }
                    } else {
                        zipOut.putNextEntry(new ZipEntry(zipEntryName));
                        Files.copy(path, zipOut);
                        zipOut.closeEntry();
                    }
                } catch (IOException e) {
                    log.error("Ошибка при добавлении файла {} в архив: {}", path, e.getMessage());
                    throw new RuntimeException("Ошибка при добавлении файла в архив.", e);
                }
            });

            // Явно закрываем поток перед перемещением файла
            zipOut.flush();
            zipOut.close(); // Явное закрытие ZipOutputStream

            log.info("Архив создан во временном файле: {}", temporaryBackupFilePath);

            // Проверяем, что временный файл создан и не используется другим процессом
            if (Files.exists(temporaryBackupFilePath)) {
                log.info("Файл '{}' существует перед перемещением", temporaryBackupFilePath);
            } else {
                log.warn("Файл '{}' не существует перед перемещением!", temporaryBackupFilePath);
            }

            // Перемещаем временный файл в итоговый архив
            Files.move(temporaryBackupFilePath, finalBackupFilePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Резервная копия успешно перемещена из '{}' в '{}'", temporaryBackupFilePath, finalBackupFilePath);

            // Вычисляем контрольную сумму архива
            String checksum = FileService.calculateFileChecksum(finalBackupFilePath);
            result.put(finalBackupFilePath.getFileName().toString(), checksum);

        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Ошибка при создании резервной копии директории '{}': {}", uploadDocumentationPath, e.getMessage());

            // Удаляем временный архив в случае ошибки
            try {
                Files.deleteIfExists(temporaryBackupFilePath);
                log.info("Поврежденный временный архив удален: {}", temporaryBackupFilePath);
            } catch (IOException deleteEx) {
                log.error("Не удалось удалить временный архив '{}': {}", temporaryBackupFilePath, deleteEx.getMessage());
            }

            // Возвращаем пустую мапу при ошибке
            return Collections.emptyMap();
        }

        return result;
    }

    // Метод для удаления резервной копии
    public boolean deleteBackup(Long id) {
        // Получаем резервную копию по ID
        Optional<Backup> backupOptional = backupRepository.findById(id);

        if (backupOptional.isPresent()) {
            Backup backup = backupOptional.get();

            // Удаляем архив с диска
            try {
                Path archivePath = Paths.get(uploadBackupPath, backup.getArchiveName());
                Files.deleteIfExists(archivePath);
                log.info("Архив '{}' успешно удалён.", archivePath);
            } catch (IOException e) {
                log.error("Ошибка при удалении архива '{}': {}", backup.getArchiveName(), e.getMessage());
                return false;
            }

            // Удаляем запись из базы данных
            backupRepository.delete(backup);
            log.info("Резервная копия с ID '{}' успешно удалена.", id);
            return true;
        } else {
            log.warn("Резервная копия с ID '{}' не найдена.", id);
            return false;
        }
    }

//    При создани бекапа не создатеся Тип если он не имеет документа

    // Метод для восстановления данных из резервной копии
    @Transactional
    public boolean restoreFromBackup(Path backupArchivePath) {
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(backupArchivePath.toFile()))) {
            ZipEntry entry;

            log.info("Начинаем восстановление данных из архива '{}'", backupArchivePath);

            // Удаляем все старые данные из базы и файловой системы
            clearCurrentData();

            // Восстанавливаем данные из архива
            while ((entry = zipIn.getNextEntry()) != null) {
                log.info("Обрабатываем элемент архива: '{}'", entry.getName());

                Path outputPath = Paths.get(uploadDocumentationPath, entry.getName());

                // Пропускаем корневую директорию "documentation"
                if (outputPath.equals(Paths.get(uploadDocumentationPath)) || entry.getName().equals(uploadDocumentationPath+"/")) {
                    log.info("Пропускаем корневую директорию '{}'", outputPath);
                    zipIn.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                    log.info("Создана директория '{}'", outputPath);

                    // Восстанавливаем категорию или тип
                    String[] pathParts = outputPath.subpath(1, outputPath.getNameCount()).toString().split(Pattern.quote(File.separator));
                    if (pathParts.length == 1) {
                        String categoryName = pathParts[0];
                        restoreCategory(categoryName);
                    } else if (pathParts.length == 2) {
                        String categoryName = pathParts[0];
                        String typeName = pathParts[1];
                        restoreCategoryWithType(categoryName, typeName);
                    }
                } else {
                    // Это файл, создаем директории и восстанавливаем файл
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zipIn, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Файл '{}' восстановлен", outputPath);

                    // Восстанавливаем данные для каждой сущности (Category, Type, Documentation)
                    restoreEntityFromFile(outputPath);
                }

                zipIn.closeEntry();
            }

            log.info("Восстановление данных из архива '{}' завершено успешно.", backupArchivePath);
            return true;

        } catch (IOException e) {
            log.error("Ошибка при восстановлении данных из архива '{}': {}", backupArchivePath, e.getMessage());
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }



    // Метод для очистки текущих данных
    private void clearCurrentData() {
        // Удаляем все документы, типы и категории
        documentationService.getDocumentationRepository().deleteAll();
        typeDocumentationService.getTypeDocumentationRepository().deleteAll();
        categoryDocumentationService.getCategoryDocumentationRepository().deleteAll();

        // Удаляем все файлы из директории
        try {
            FileService.deleteDirectoryRecursively(Paths.get(uploadDocumentationPath));
            Files.createDirectories(Paths.get(uploadDocumentationPath)); // Создаем директорию заново
        } catch (IOException e) {
            log.error("Ошибка при очистке директории '{}': {}", uploadDocumentationPath, e.getMessage());
        }
    }

    // Восстановление сущностей из файлов
    private void restoreEntityFromFile(Path filePath) {
        try {
            // Получаем путь файла относительно директории хранения (например, "Категория/Тип/Документ.pdf")
            Path relativePath = Paths.get(uploadDocumentationPath).relativize(filePath);
            String[] pathParts = relativePath.toString().split(Pattern.quote(File.separator));

            if (pathParts.length == 1) {
                // Это категория без типов и документов
                String categoryName = pathParts[0];
                log.info("Восстанавливаем категорию '{}'", categoryName);
                restoreCategory(categoryName);
            } else if (pathParts.length == 2) {
                // Это категория с типом, но без документов
                String categoryName = pathParts[0];
                String typeName = pathParts[1];
                log.info("Восстанавливаем категорию '{}' с типом '{}'", categoryName, typeName);
                restoreCategoryWithType(categoryName, typeName);
            } else if (pathParts.length == 3) {
                // Это категория с типом и документами
                String categoryName = pathParts[0];
                String typeName = pathParts[1];
                String documentFileName = pathParts[2];
                log.info("Восстанавливаем категорию '{}', тип '{}' и документ '{}'", categoryName, typeName, documentFileName);
                restoreCategoryTypeAndDocument(categoryName, typeName, documentFileName, filePath);
            } else {
                log.warn("Неподдерживаемый формат пути: {}", filePath);
            }

        } catch (IOException e) {
            log.error("Ошибка при восстановлении данных из файла '{}': {}", filePath, e.getMessage(), e);
        }
    }

    // Метод для восстановления категории
    private void restoreCategory(String categoryName) {
        // Проверяем, существует ли категория, и создаём её, если нет
        categoryDocumentationService.getCategoryDocumentationRepository()
                .findByCategory(categoryName)
                .orElseGet(() -> {
                    CategoryDocumentation newCategory = new CategoryDocumentation(categoryName);
                    boolean saved = categoryDocumentationService.saveCategoryDocumentation(newCategory);
                    if (saved) {
                        log.info("Категория '{}' восстановлена без типов и документов.", categoryName);
                    } else {
                        log.error("Ошибка при восстановлении категории '{}'.", categoryName);
                    }
                    return newCategory;
                });
    }

    // Метод для восстановления категории и типа без документов
    private void restoreCategoryWithType(String categoryName, String typeName) {
        // Восстанавливаем категорию
        CategoryDocumentation category = categoryDocumentationService.getCategoryDocumentationRepository()
                .findByCategory(categoryName)
                .orElseGet(() -> {
                    CategoryDocumentation newCategory = new CategoryDocumentation(categoryName);
                    categoryDocumentationService.saveCategoryDocumentation(newCategory);
                    log.info("Категория '{}' восстановлена.", categoryName);
                    return newCategory;
                });

        // Восстанавливаем тип
        TypeDocumentation type = typeDocumentationService.getTypeDocumentationRepository()
                .findByCategoryDocumentationAndType(category, typeName)
                .orElseGet(() -> {
                    TypeDocumentation newType = new TypeDocumentation(category, typeName);
                    typeDocumentationService.getTypeDocumentationRepository().save(newType);
                    log.info("Тип '{}' для категории '{}' восстановлен.", typeName, categoryName);
                    return newType;
                });
    }

    // Метод для восстановления категории, типа и документа
    private void restoreCategoryTypeAndDocument(String categoryName, String typeName, String documentFileName, Path filePath) throws IOException {
        // Восстанавливаем категорию
        CategoryDocumentation category = categoryDocumentationService.getCategoryDocumentationRepository()
                .findByCategory(categoryName)
                .orElseGet(() -> {
                    CategoryDocumentation newCategory = new CategoryDocumentation(categoryName);
                    categoryDocumentationService.saveCategoryDocumentation(newCategory);
                    return newCategory;
                });

        // Восстанавливаем тип
        TypeDocumentation type = typeDocumentationService.getTypeDocumentationRepository()
                .findByCategoryDocumentationAndType(category, typeName)
                .orElseGet(() -> {
                    TypeDocumentation newType = new TypeDocumentation(category, typeName);
                    typeDocumentationService.getTypeDocumentationRepository().save(newType);
                    return newType;
                });

        // Убедимся, что тип был сохранён
        typeDocumentationService.getTypeDocumentationRepository().flush();

        // Восстанавливаем документ
        if (documentFileName != null && !documentFileName.isEmpty()) {
            Path targetDocumentPath = Paths.get(uploadDocumentationPath, categoryName, typeName, documentFileName);
            Files.copy(filePath, targetDocumentPath, StandardCopyOption.REPLACE_EXISTING);

            Documentation documentation = new Documentation(type, Paths.get(categoryName, typeName, documentFileName).toString());
            documentationService.getDocumentationRepository().save(documentation);

            log.info("Документ '{}' для категории '{}' и типа '{}' восстановлен.", documentFileName, categoryName, typeName);
        } else {
            log.info("Тип '{}' для категории '{}' восстановлен без документов.", typeName, categoryName);
        }
    }
}