package com.academy.catalog.component;

import com.academy.catalog.models.Role;
import com.academy.catalog.models.User;
import com.academy.catalog.repo.RoleRepository;
import com.academy.catalog.repo.UserRepository;
import com.academy.catalog.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class RoleRunner implements CommandLineRunner {
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_VISITOR = "ROLE_VISITOR";

    private final RoleRepository roleRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    public RoleRunner(RoleRepository roleRepository, UserService userService, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        createRoleIfNotFound(ROLE_ADMIN);
        createRoleIfNotFound(ROLE_VISITOR);
        createAdminIfNotFound();
    }

    private void createRoleIfNotFound(String roleName) {
        Optional<Role> roleOptional  = roleRepository.findByName(roleName);
        if (roleOptional.isEmpty()) {
            log.info("Роли = " + roleName + " не существует в БД. Создаем роль.");
            Role role = new Role();
            role.setName(roleName);
            roleRepository.save(role);
            log.info("Создана роль: {}", roleName);
        }
    }

    private void createAdminIfNotFound() {
        if(userRepository.findByRoleName("ROLE_ADMIN").isEmpty()){
            log.info("Администратора = не существует в БД. Создаем администратора по умолчанию.");
            User admin = new User("Техническая", "учетная", "запись", "admin", "admin", 5, "Факультет");
            userService.saveAdmin(admin);
        }
    }
}