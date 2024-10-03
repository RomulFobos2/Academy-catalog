package com.academy.catalog.controllers.admin;

import com.academy.catalog.models.CategoryDocumentation;
import com.academy.catalog.models.Documentation;
import com.academy.catalog.models.TypeDocumentation;
import com.academy.catalog.service.CategoryDocumentationService;
import com.academy.catalog.service.DocumentationService;
import com.academy.catalog.service.TypeDocumentationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@Slf4j
public class TypeDocumentationController {

    private final CategoryDocumentationService categoryDocumentationService;
    private final TypeDocumentationService typeDocumentationService;
    private final DocumentationService documentationService;

    public TypeDocumentationController(CategoryDocumentationService categoryDocumentationService,
                                       TypeDocumentationService typeDocumentationService,
                                       DocumentationService documentationService) {
        this.categoryDocumentationService = categoryDocumentationService;
        this.typeDocumentationService = typeDocumentationService;
        this.documentationService = documentationService;
    }

    //TODO можно перенести в сервисный класс?
    @GetMapping("/admin/typeDocumentations/checkTypeDocumentationExists")
    public ResponseEntity<Boolean> checkTypeDocumentationExists(@RequestParam String typeName,
                                                                @RequestParam long categoryDocumentationId) {
        log.info("Проверка типа \"{}\".", typeName);
        Optional<CategoryDocumentation> categoryDocumentationOptional = categoryDocumentationService.getCategoryDocumentationRepository()
                .findById(categoryDocumentationId);
        if (categoryDocumentationOptional.isEmpty()) {
            log.info("Категория документации по заданному id = \"{}\" не найдена.", categoryDocumentationId);
            return ResponseEntity.status(404).body(false);
        } else {
            boolean exists = typeDocumentationService.getTypeDocumentationRepository()
                    .existsByTypeAndCategoryDocumentation(typeName, categoryDocumentationOptional.get());
            return ResponseEntity.ok(exists);
        }
    }

    @GetMapping("/admin/typeDocumentations/allTypeDocumentations")
    public String allTypeDocumentations(Model model) {
        List<CategoryDocumentation> allCategories = categoryDocumentationService
                .getCategoryDocumentationRepository().findAll();

        Map<CategoryDocumentation, List<TypeDocumentation>> categoryTypeMap = new LinkedHashMap<>();

        for (CategoryDocumentation category : allCategories) {
            List<TypeDocumentation> types = typeDocumentationService.getTypeDocumentationRepository()
                    .findByCategoryDocumentation(category);
            categoryTypeMap.put(category, types);
        }

        model.addAttribute("categoryTypeMap", categoryTypeMap);
        return "admin/typeDocumentations/allTypeDocumentations";
    }

    @GetMapping("/admin/typeDocumentations/addTypeDocumentation")
    public String addTypeDocumentation(Model model) {
        List<CategoryDocumentation> allCategoryDocumentations = categoryDocumentationService
                .getCategoryDocumentationRepository().findAll();
        model.addAttribute("allCategoryDocumentations", allCategoryDocumentations);
        return "admin/typeDocumentations/addTypeDocumentation";
    }

    @PostMapping("/admin/typeDocumentations/addTypeDocumentation")
    public String addTypeDocumentation(@RequestParam long categoryDocumentationId,
                                       @RequestParam String inputType,
                                       Model model) {
        if (!typeDocumentationService.saveTypeDocumentation(categoryDocumentationId, inputType.trim())) {
            model.addAttribute("typeDocumentationNameError", "Ошибка при сохранении. Подробности смотрите в логах.");
            List<CategoryDocumentation> allCategoryDocumentations = categoryDocumentationService
                    .getCategoryDocumentationRepository().findAll();
            model.addAttribute("allCategoryDocumentations", allCategoryDocumentations);
            return "admin/typeDocumentations/addTypeDocumentation";
        } else {
            return "redirect:/admin/typeDocumentations/allTypeDocumentations";
        }
    }

    @GetMapping("/admin/typeDocumentations/editTypeDocumentation/{id}")
    public String editTypeDocumentation(@PathVariable(value = "id") long id, Model model) {
        if (!typeDocumentationService.getTypeDocumentationRepository().existsById(id)) {
            return "redirect:/admin/typeDocumentations/allTypeDocumentations";
        }
        TypeDocumentation typeDocumentation = typeDocumentationService.getTypeDocumentationRepository().findById(id).get();
        model.addAttribute("typeDocumentation", typeDocumentation);
        List<CategoryDocumentation> allCategoryDocumentations = categoryDocumentationService
                .getCategoryDocumentationRepository().findAll();
        model.addAttribute("allCategoryDocumentations", allCategoryDocumentations);
        return "admin/typeDocumentations/editTypeDocumentation";
    }


    @PostMapping("/admin/typeDocumentations/editTypeDocumentation/{id}")
    public String editTypeDocumentation(@PathVariable(value = "id") long id,
                                        @RequestParam long categoryDocumentationId,
                                            @RequestParam String inputType,
                                            Model model, RedirectAttributes redirectAttributes) {
        if (!typeDocumentationService.editTypeDocumentation(id, categoryDocumentationId, inputType.trim())) {
            redirectAttributes.addFlashAttribute("typeDocumentationNameError", "Ошибка при сохранении изменений. Подробности смотрите в логах.");
            return "redirect:/admin/typeDocumentations/editTypeDocumentation/" + id;
        } else {
            return "redirect:/admin/typeDocumentations/allTypeDocumentations";
        }
    }

    @GetMapping("/admin/typeDocumentations/deleteTypeDocumentation/{id}")
    public String confirmDeleteTypeDocumentation(@PathVariable(value = "id") long id, Model model) {
        Optional<TypeDocumentation> typeDocumentationOpt = typeDocumentationService.getTypeDocumentationRepository().findById(id);
        if (typeDocumentationOpt.isEmpty()) {
            return "redirect:/admin/typeDocumentations/allTypeDocumentations";
        }

        TypeDocumentation typeDocumentation = typeDocumentationOpt.get();
        List<Documentation> documents = documentationService.getDocumentationRepository().findByTypeDocumentationId(id);

        model.addAttribute("typeDocumentation", typeDocumentation);
        model.addAttribute("documents", documents);

        return "admin/typeDocumentations/confirmDeleteTypeDocumentation";
    }

    @PostMapping("/admin/typeDocumentations/deleteTypeDocumentation/{id}")
    public String deleteTypeDocumentation(@PathVariable(value = "id") long id,
                                          @RequestParam String confirmation,
                                          RedirectAttributes redirectAttributes) {
        if (!"Удалить".equals(confirmation.trim())) {
            redirectAttributes.addFlashAttribute("error", "Вы должны ввести слово 'Удалить' для подтверждения.");
            return "redirect:/admin/typeDocumentations/deleteTypeDocumentation/" + id;
        }

        if (!typeDocumentationService.deleteTypeDocumentation(id)) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при удалении типа документации. Подробности в логах.");
            return "redirect:/admin/typeDocumentations/deleteTypeDocumentation/" + id;
        }

        redirectAttributes.addFlashAttribute("success", "Тип документации успешно удалён.");
        return "redirect:/admin/typeDocumentations/allTypeDocumentations";
    }


}
