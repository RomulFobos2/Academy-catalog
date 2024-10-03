package com.academy.catalog.controllers.admin;

import com.academy.catalog.models.CategoryDocumentation;
import com.academy.catalog.models.Documentation;
import com.academy.catalog.models.TypeDocumentation;
import com.academy.catalog.service.CategoryDocumentationService;
import com.academy.catalog.service.DocumentationService;
import com.academy.catalog.service.TypeDocumentationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;


@Controller
@Slf4j
public class DocumentationController {

    private final CategoryDocumentationService categoryDocumentationService;
    private final TypeDocumentationService typeDocumentationService;
    private final DocumentationService documentationService;
    @Value("${upload.documentation.path}")
    private String uploadDocumentationPath;


    public DocumentationController(CategoryDocumentationService categoryDocumentationService,
                                   TypeDocumentationService typeDocumentationService,
                                   DocumentationService documentationService) {
        this.categoryDocumentationService = categoryDocumentationService;
        this.typeDocumentationService = typeDocumentationService;
        this.documentationService = documentationService;
    }

    @GetMapping("/admin/documentations/allDocumentations")
    public String allDocumentations(Model model) {
        model.addAttribute("allCategoryDocumentations", categoryDocumentationService.getCategoryDocumentationRepository().findAll());
        return "admin/documentations/allDocumentations";
    }

    @GetMapping("/admin/documentations/types")
    @ResponseBody
    public List<TypeDocumentation> getTypeDocumentations(@RequestParam("categoryId") Long categoryId) {
        CategoryDocumentation categoryDocumentation = new CategoryDocumentation();
        categoryDocumentation.setId(categoryId);
        return typeDocumentationService.getTypeDocumentationRepository().findByCategoryDocumentation(categoryDocumentation);
    }

    @GetMapping("/admin/documentations/check")
    @ResponseBody
    public boolean checkDocumentationExists(@RequestParam("categoryId") Long categoryId,
                                            @RequestParam("typeId") Long typeId,
                                            @RequestParam("fileName") String fileName) {
        // Логируем входящие параметры
        log.info("Получен запрос на проверку документации:");
        log.info("Категория ID: {}", categoryId);
        log.info("Тип ID: {}", typeId);
        log.info("Имя файла: {}", fileName);

        // Загружаем объекты CategoryDocumentation и TypeDocumentation
        Optional<CategoryDocumentation> categoryDocumentationOptional = categoryDocumentationService.getCategoryDocumentationRepository().findById(categoryId);
        Optional<TypeDocumentation> typeDocumentationOptional = typeDocumentationService.getTypeDocumentationRepository().findById(typeId);

        // Проверяем существование категории и типа документации
        if (categoryDocumentationOptional.isEmpty()) {
            log.error("Категория с ID {} не найдена!", categoryId);
            return false;
        }
        if (typeDocumentationOptional.isEmpty()) {
            log.error("Тип документации с ID {} не найден!", typeId);
            return false;
        }

        CategoryDocumentation categoryDocumentation = categoryDocumentationOptional.get();
        TypeDocumentation typeDocumentation = typeDocumentationOptional.get();

        // Логируем загруженные объекты
        log.info("Найдена категория: {}", categoryDocumentation.getCategory());
        log.info("Найден тип документации: {}", typeDocumentation.getType());

        // Формируем путь к файлу
        String filePath = String.join(File.separator,
                categoryDocumentation.getCategory(),
                typeDocumentation.getType(),
                fileName);

        log.info("Сформированный путь к файлу: {}", filePath);

        // Проверяем, существует ли уже документ с таким типом и путём
        boolean exists = documentationService.getDocumentationRepository().existsByTypeDocumentationAndFilePath(typeDocumentation, filePath);
        log.info("Результат проверки документации: {}", exists ? "Документ существует" : "Документ не найден");

        return exists;
    }

    @GetMapping("/admin/documentations/byType")
    @ResponseBody
    public List<Documentation> getDocumentationsByType(@RequestParam("typeId") Long typeId) {
        return documentationService.getDocumentationRepository().findByTypeDocumentationId(typeId);
    }

    @GetMapping("/documentation")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@RequestParam String filePath) {
        try {
            Path file = Paths.get(uploadDocumentationPath).resolve(filePath);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(resource);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/admin/documentations/addDocumentation")
    public String addDocumentation(Model model) {
        List<CategoryDocumentation> allCategoryDocumentations = categoryDocumentationService
                .getCategoryDocumentationRepository().findAll();
        model.addAttribute("allCategoryDocumentations", allCategoryDocumentations);
        return "admin/documentations/addDocumentation";
    }

    @PostMapping("/admin/documentations/addDocumentation")
    public String addDocumentation(@RequestParam long typeDocumentationId,
                                   @RequestParam MultipartFile inputFileField,
                                   Model model) {
        if (!"application/pdf".equals(inputFileField.getContentType())) {
            model.addAttribute("documentationNameError", "Допустимы только файлы PDF.");
            List<CategoryDocumentation> allCategoryDocumentations = categoryDocumentationService
                    .getCategoryDocumentationRepository().findAll();
            model.addAttribute("allCategoryDocumentations", allCategoryDocumentations);
            return "admin/documentations/addDocumentation";
        }
        if (!documentationService.saveDocumentation(typeDocumentationId, inputFileField)) {
            model.addAttribute("documentationNameError", "Ошибка при сохранении. Подробности смотрите в логах.");
            List<CategoryDocumentation> allCategoryDocumentations = categoryDocumentationService
                    .getCategoryDocumentationRepository().findAll();
            model.addAttribute("allCategoryDocumentations", allCategoryDocumentations);
            return "admin/documentations/addDocumentation";
        } else {
            return "redirect:/admin/documentations/allDocumentations";
        }
    }

    @GetMapping("/admin/documentations/editDocumentation/{id}")
    public String editDocumentation(Model model, @PathVariable(value = "id") long id) {
        if (!documentationService.getDocumentationRepository().existsById(id)) {
            return "redirect:/admin/documentations/allDocumentations";
        }
        Documentation documentation = documentationService.getDocumentationRepository().findById(id).get();
        model.addAttribute("documentation", documentation);
        List<CategoryDocumentation> allCategoryDocumentations = categoryDocumentationService
                .getCategoryDocumentationRepository().findAll();
        model.addAttribute("allCategoryDocumentations", allCategoryDocumentations);
        return "admin/documentations/editDocumentation";
    }


    @PostMapping("/admin/documentations/editDocumentation/{id}")
    public String editDocumentation(@PathVariable(value = "id") long id,
                                    @RequestParam long typeDocumentationId,
                                    @RequestParam(required = false) MultipartFile inputFileField,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        if (inputFileField != null && !inputFileField.isEmpty()) {
            if (!"application/pdf".equals(inputFileField.getContentType())) {
                redirectAttributes.addFlashAttribute("documentationNameError", "Допустимы только файлы PDF.");
                return "redirect:/admin/documentations/editDocumentation/" + id;
            }
        }

        if (!documentationService.editDocumentation(id, typeDocumentationId, inputFileField)) {
            redirectAttributes.addFlashAttribute("documentationNameError",
                    "Ошибка при сохранении. Подробности смотрите в логах.");
            return "redirect:/admin/documentations/editDocumentation/" + id;
        } else {
            return "redirect:/admin/documentations/allDocumentations";
        }
    }

    @DeleteMapping("/admin/documentations/deleteDocumentation/{id}")
    @ResponseBody
    public ResponseEntity<String> deleteDocumentation(@PathVariable("id") Long id) {
        boolean isDeleted = documentationService.deleteDocumentation(id);

        if (isDeleted) {
            return ResponseEntity.ok("Документация успешно удалена.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Документация не найдена или произошла ошибка при удалении.");
        }
    }

}
