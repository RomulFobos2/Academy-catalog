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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Controller
@Slf4j
public class CategoryDocumentationController {

    private final CategoryDocumentationService categoryDocumentationService;
    private final TypeDocumentationService typeDocumentationService;
    private final DocumentationService documentationService;

    public CategoryDocumentationController(CategoryDocumentationService categoryDocumentationService,
                                           TypeDocumentationService typeDocumentationService,
                                           DocumentationService documentationService) {
        this.categoryDocumentationService = categoryDocumentationService;
        this.typeDocumentationService = typeDocumentationService;
        this.documentationService = documentationService;
    }

    @GetMapping("/admin/categoryDocumentations/checkCategoryDocumentationExists")
    public ResponseEntity<Boolean> checkCategoryDocumentationExists(@RequestParam String categoryName) {
        log.info("Проверка категории \"{}\".", categoryName);
        boolean exists = categoryDocumentationService.getCategoryDocumentationRepository()
                .existsByCategory(categoryName);
        return ResponseEntity.ok(exists);
    }

//    @GetMapping("/admin/categoryDocumentations/allCategoryDocumentations")
//    public String allCategoryDocumentations(Model model) {
//        List<CategoryDocumentation> allCategoryDocumentations =
//                categoryDocumentationService.getCategoryDocumentationRepository().findAll();
//        model.addAttribute("allCategoryDocumentations", allCategoryDocumentations);
//        return "admin/categoryDocumentations/allCategoryDocumentations";
//    }

    @GetMapping("/admin/categoryDocumentations/addCategoryDocumentation")
    public String addCategoryDocumentation(Model model) {
        return "admin/categoryDocumentations/addCategoryDocumentation";
    }

    @PostMapping("/admin/categoryDocumentations/addCategoryDocumentation")
    public String addCategoryDocumentation(@RequestParam String inputCategory, Model model) throws IOException {
        CategoryDocumentation categoryDocumentation = new CategoryDocumentation(inputCategory.trim());
        if (!categoryDocumentationService.saveCategoryDocumentation(categoryDocumentation)) {
            model.addAttribute("categoryDocumentationNameError", "Ошибка при сохранении. Подробности смотрите в логах.");
            return "admin/categoryDocumentations/addCategoryDocumentation";
        } else {
//            return "redirect:/admin/categoryDocumentations/allCategoryDocumentations";
            return "redirect:/admin/typeDocumentations/allTypeDocumentations";
        }
    }

    @GetMapping("/admin/categoryDocumentations/editCategoryDocumentation/{id}")
    public String editCategoryDocumentation(@PathVariable(value = "id") long id, Model model) {
        if (!categoryDocumentationService.getCategoryDocumentationRepository().existsById(id)) {
//            return "redirect:/admin/categoryDocumentations/allCategoryDocumentations";
            return "redirect:/admin/typeDocumentations/allTypeDocumentations";
        }
        CategoryDocumentation categoryDocumentation = categoryDocumentationService.getCategoryDocumentationRepository().findById(id).get();
        model.addAttribute("categoryDocumentation", categoryDocumentation);
        return "admin/categoryDocumentations/editCategoryDocumentation";
    }


    @PostMapping("/admin/categoryDocumentations/editCategoryDocumentation/{id}")
    public String editCategoryDocumentation(@PathVariable(value = "id") long id,
                                            @RequestParam String inputCategory,
                                            Model model, RedirectAttributes redirectAttributes) {
        if (!categoryDocumentationService.editCategoryDocumentation(id, inputCategory.trim())) {
            redirectAttributes.addFlashAttribute("categoryDocumentationNameError", "Ошибка при сохранении изменений. Подробности смотрите в логах.");
            return "redirect:/admin/categoryDocumentations/editCategoryDocumentation/" + id;
        } else {
//            return "redirect:/admin/categoryDocumentations/allCategoryDocumentations";
            return "redirect:/admin/typeDocumentations/allTypeDocumentations";
        }
    }


    @GetMapping("/admin/categoryDocumentations/deleteCategoryDocumentation/{id}")
    public String confirmDeleteCategoryDocumentation(@PathVariable(value = "id") long id, Model model) {
        Optional<CategoryDocumentation> categoryDocumentationOpt = categoryDocumentationService.getCategoryDocumentationRepository().findById(id);
        if (categoryDocumentationOpt.isEmpty()) {
            return "redirect:/admin/typeDocumentations/allTypeDocumentations";
        }

        CategoryDocumentation categoryDocumentation = categoryDocumentationOpt.get();
        List<TypeDocumentation> types = typeDocumentationService.getCategoryTypes(categoryDocumentation);
        List<Documentation> documents = documentationService.getAllDocumentsByCategoryId(categoryDocumentation);

        model.addAttribute("categoryDocumentation", categoryDocumentation);
        model.addAttribute("types", types);
        model.addAttribute("documents", documents);

        return "admin/categoryDocumentations/confirmDeleteCategoryDocumentation";
    }

    @PostMapping("/admin/categoryDocumentations/deleteCategoryDocumentation/{id}")
    public String deleteCategoryDocumentation(@PathVariable(value = "id") long id,
                                              @RequestParam String confirmation,
                                              RedirectAttributes redirectAttributes) {
        if (!"Удалить".equals(confirmation.trim())) {
            redirectAttributes.addFlashAttribute("error", "Вы должны ввести слово 'Удалить' для подтверждения.");
            return "redirect:/admin/categoryDocumentations/deleteCategoryDocumentation/" + id;
        }

        if (!categoryDocumentationService.deleteCategoryDocumentation(id)) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при удалении категории. Подробности в логах.");
            return "redirect:/admin/categoryDocumentations/deleteCategoryDocumentation/" + id;
        }

        redirectAttributes.addFlashAttribute("success", "Категория успешно удалена.");
        return "redirect:/admin/typeDocumentations/allTypeDocumentations";
    }
}