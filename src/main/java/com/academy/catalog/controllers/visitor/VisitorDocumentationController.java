package com.academy.catalog.controllers.visitor;

import com.academy.catalog.models.CategoryDocumentation;
import com.academy.catalog.models.Documentation;
import com.academy.catalog.models.TypeDocumentation;
import com.academy.catalog.models.User;
import com.academy.catalog.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;


@Controller
@Slf4j
public class VisitorDocumentationController {

    private final CategoryDocumentationService categoryDocumentationService;
    private final TypeDocumentationService typeDocumentationService;
    private final DocumentationService documentationService;
    private final UserService userService;
    private final VisitorActionService visitorActionService;

    @Value("${upload.documentation.path}")
    private String uploadDocumentationPath;


    public VisitorDocumentationController(CategoryDocumentationService categoryDocumentationService,
                                          TypeDocumentationService typeDocumentationService,
                                          DocumentationService documentationService,
                                          UserService userService, VisitorActionService visitorActionService) {
        this.categoryDocumentationService = categoryDocumentationService;
        this.typeDocumentationService = typeDocumentationService;
        this.documentationService = documentationService;
        this.userService = userService;
        this.visitorActionService = visitorActionService;
    }

    @GetMapping("/visitor/documentations/allDocumentations")
    public String allDocumentations(Model model) {
        model.addAttribute("allCategoryDocumentations", categoryDocumentationService.getCategoryDocumentationRepository().findAll());
        return "visitor/documentations/allDocumentations";
    }

    @GetMapping("/visitor/documentations/types")
    @ResponseBody
    public List<TypeDocumentation> getTypeDocumentations(@RequestParam("categoryId") Long categoryId) {
        CategoryDocumentation categoryDocumentation = new CategoryDocumentation();
        categoryDocumentation.setId(categoryId);
        return typeDocumentationService.getTypeDocumentationRepository().findByCategoryDocumentation(categoryDocumentation);
    }


    @GetMapping("/visitor/documentations/byType")
    @ResponseBody
    public List<Documentation> getDocumentationsByType(@RequestParam("typeId") Long typeId) {
        return documentationService.getDocumentationRepository().findByTypeDocumentationId(typeId);
    }

    @GetMapping("/visitor/documentation")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@RequestParam String filePath) {
        try {
            Path file = Paths.get(uploadDocumentationPath).resolve(filePath);
            Resource resource = new UrlResource(file.toUri());
            Optional<User> currentUserOptional = userService.getUserRepository().findByUsername(SecurityContextHolder.getContext().getAuthentication().getName());
            User currentUser = currentUserOptional.get();
            visitorActionService.saveVisitorAction(currentUser.getUsername(), currentUser.getFullName(), filePath);
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

}
