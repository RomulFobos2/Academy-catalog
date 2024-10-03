package com.academy.catalog.controllers.general;

import com.academy.catalog.models.User;
import com.academy.catalog.models.VisitorAction;
import com.academy.catalog.service.UserService;
import com.academy.catalog.service.VisitorActionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;


@Controller
public class MainController {
    private final UserService userService;
    private final VisitorActionService visitorActionService;

    public MainController(UserService userService, VisitorActionService visitorActionService) {
        this.userService = userService;
        this.visitorActionService = visitorActionService;
    }

    @GetMapping("/")
    public String home(Model model) {
        Optional<User> currentUserOptional = userService.getUserRepository().findByUsername(SecurityContextHolder.getContext().getAuthentication().getName());
        if(currentUserOptional.isPresent() && currentUserOptional.get().isNeedChangePass()){
            return "redirect:/change-password";
        }
        return "general/home";
    }

    @GetMapping("/login")
    public String login(Model model) {
        return "general/login";
    }

    @GetMapping("/change-password")
    public String changePassword(Model model) {
        Optional<User> currentUser = userService.getUserRepository().findByUsername(SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("currentUser", currentUser.get());
        return "general/change-password";
    }

    //TODO переделать
    @PostMapping("/change-password")
    public String changePassword(@RequestParam String passwordNew, @RequestParam String passwordConfirm, Model model) {
        //Получаем текущего пользователя, который инициировал смену пароля
        Optional<User> currentUserOptional = userService.getUserRepository().findByUsername(SecurityContextHolder.getContext().getAuthentication().getName());
        if (passwordNew.equals(passwordConfirm)) {
            currentUserOptional.get().setPassword(userService.getBCryptPasswordEncoder().encode(passwordNew));
            currentUserOptional.get().setNeedChangePass(false);
            userService.getUserRepository().save(currentUserOptional.get());
        }
        else {
            model.addAttribute("passwordError", "Пароли не совпадают. Подтвердите новый пароль верно.");
            return "general/change-password";
        }
        return "redirect:/logout";
    }

    @GetMapping("/profile")
    public String profile(@RequestParam(value = "page", defaultValue = "0") int page, Model model) {
        // Загружаем объект текущего пользователя
        Optional<User> currentUserOptional = userService.getUserRepository().findByUsername(SecurityContextHolder.getContext().getAuthentication().getName());
        User currentUser = currentUserOptional.get();
        String roleName = currentUserOptional.get().getRole().getName();

        switch (roleName){
            case "ROLE_VISITOR":
                roleName = "Пользователь";

                // Пагинация: выводим по 15 записей на странице, сортировка по дате убывания
                Pageable pageable = PageRequest.of(page, 10, Sort.by("timeOfVisit").descending());
                Page<VisitorAction> visitorActionsPage = visitorActionService.getVisitorActionRepository().findByUsernameAndVisitorFullNameOrderByTimeOfVisitDesc(currentUser.getUsername(), currentUser.getFullName(), pageable);

                // Добавляем данные в модель
                model.addAttribute("allVisitorActions", visitorActionsPage.getContent()); // сами записи
                model.addAttribute("currentPage", page);
                model.addAttribute("totalPages", visitorActionsPage.getTotalPages());
                break;

            default:
                roleName = "Администратор";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("roleName", roleName);
        return "general/profile";
    }


}
