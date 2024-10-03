package com.academy.catalog.controllers.admin;

import com.academy.catalog.models.User;
import com.academy.catalog.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@Slf4j
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/admin/users/check-username")
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam String username) {
        log.info("Проверка имени пользователя {}.", username);
        boolean exists = userService.checkUserName(username);
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/users/allUsers")
    public String allUsers(
            @RequestParam(value = "searchCategory", required = false, defaultValue = "all") String searchCategory,
            @RequestParam(value = "searchInput", required = false) String searchInput,
            @RequestParam(value = "searchSort", required = false, defaultValue = "asc") String searchSort,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size, // Изменил значение по умолчанию на 10
            Model model) {

        boolean sortDescending = searchSort.equals("desc");
        Page<User> usersPage;

        // Если параметры поиска не указаны, выводим всех пользователей
        if (searchInput == null && searchCategory.equals("all")) {
            Pageable pageable = PageRequest.of(page, size, Sort.by("lastName").ascending()); // Сортировка по умолчанию
            usersPage = userService.getAllUsers(pageable);
        } else {
            // Если указан параметр поиска, продолжаем логику
            switch (searchCategory) {
                case "lastName":
                    usersPage = userService.findUsersByLastName(searchInput, sortDescending, page, size);
                    break;
                case "firstName":
                    usersPage = userService.findUsersByFirstName(searchInput, sortDescending, page, size);
                    break;
                case "patronymicName":
                    usersPage = userService.findUsersByPatronymicName(searchInput, sortDescending, page, size);
                    break;
                case "username":
                    usersPage = userService.findUsersByUsername(searchInput, sortDescending, page, size);
                    break;
                case "course":
                    int course = Integer.parseInt(searchInput);
                    usersPage = userService.findUsersByCourse(course, sortDescending, page, size);
                    break;
                case "faculty":
                    usersPage = userService.findUsersByFaculty(searchInput, sortDescending, page, size);
                    break;
                case "dateOfRegistration":
                    usersPage = userService.findUsersByDateOfRegistration(startDate, endDate, sortDescending, page, size);
                    break;
                default:
                    usersPage = userService.findUsersByMultipleFields(searchInput, page, size);  // Поиск по всем столбцам
                    break;
            }
        }

        //Фильтруем от админов
        List<User> filteredUsers = usersPage.getContent().stream()
                .filter(user -> !user.getRole().getName().equals("ROLE_ADMIN"))
                .collect(Collectors.toList());

        model.addAttribute("allUsers", filteredUsers);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", usersPage.getTotalPages());
        model.addAttribute("searchCategory", searchCategory);
        model.addAttribute("searchInput", searchInput);
        model.addAttribute("searchSort", searchSort);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "admin/users/allUsers";
    }


    @GetMapping("/admin/users/addUser")
    public String addUser(Model model) {
        return "admin/users/addUser";
    }

    @PostMapping("/admin/users/addUser")
    public String addUser(@RequestParam String inputLastName, @RequestParam String inputFirstName,
                          @RequestParam String inputPatronymicName, @RequestParam String inputUsername,
                          @RequestParam String inputPassword, @RequestParam int inputCourse,
                          @RequestParam String inputFaculty,
                          Model model) {
        User user = new User(inputLastName, inputFirstName, inputPatronymicName, inputUsername, inputPassword, inputCourse, inputFaculty);
        if (!userService.saveUser(user)) {
            model.addAttribute("usernameError", "Ошибка при сохранении. Подробности смотрите в логах.");
            return "admin/users/addUser";
        } else {
            return "redirect:/admin/users/detailsUser/" + user.getId();
        }
    }

    @GetMapping("/admin/users/detailsUser/{id}")
    public String detailsUser(@PathVariable(value = "id") long id, Model model) {
        if (!userService.getUserRepository().existsById(id)) {
            return "redirect:/admin/users/allUsers";
        }
        User user = userService.getUserRepository().findById(id).get();
        model.addAttribute("user", user);
        return "admin/users/detailsUser";
    }

    @GetMapping("/admin/users/editUser/{id}")
    public String editUser(@PathVariable(value = "id") long id, Model model) {
        if (!userService.getUserRepository().existsById(id)) {
            return "redirect:/admin/users/allUsers";
        }
        User user = userService.getUserRepository().findById(id).get();
        model.addAttribute("user", user);
        return "admin/users/editUser";
    }


    @PostMapping("/admin/users/editUser/{id}")
    public String editUser(@PathVariable(value = "id") long id,
                           @RequestParam String inputLastName, @RequestParam String inputFirstName,
                           @RequestParam String inputPatronymicName, @RequestParam String inputUsername,
                           @RequestParam int inputCourse, @RequestParam String inputFaculty,
                           Model model, RedirectAttributes redirectAttributes) {
        if (!userService.editUser(id, inputFirstName, inputLastName, inputPatronymicName,
                inputUsername, inputCourse, inputFaculty)) {
            redirectAttributes.addFlashAttribute("usernameError", "Ошибка при сохранении изменений. Подробности смотрите в логах.");
            return "redirect:/admin/users/editUser/" + id;
        } else {
            return "redirect:/admin/users/detailsUser/" + id;
        }
    }


    @PostMapping("/admin/users/resetPassword/{id}")
    public ResponseEntity<?> resetPassword(@PathVariable long id, @RequestBody Map<String, String> payload) {
        String newPassword = payload.get("newPassword");

        if (userService.resetUserPassword(id, newPassword)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("/admin/users/deleteUser/{id}")
    public String deleteUser(@PathVariable(value = "id") long id,
                           Model model, RedirectAttributes redirectAttributes) {
        if (!userService.deleteUser(id)) {
            redirectAttributes.addFlashAttribute("deleteError", "Ошибка при удалении. Подробности смотрите в логах.");
            return "redirect:/admin/users/detailsUser/" + id;
        } else {
            return "redirect:/admin/users/allUsers";
        }
    }

}
