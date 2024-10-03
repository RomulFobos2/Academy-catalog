package com.academy.catalog.service;

import com.academy.catalog.models.Role;
import com.academy.catalog.models.User;
import com.academy.catalog.repo.RoleRepository;
import com.academy.catalog.repo.UserRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@Service
@Getter
@Slf4j
public class UserService implements UserDetailsService {
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_VISITOR = "ROLE_VISITOR";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Не найден пользователь с username = " + username + "."));
    }

    @Transactional
    public boolean saveAdmin(User admin) {
        log.info("Начинаем сохранять администратора с username = {}...", admin.getUsername());

        if (checkUserName(admin.getUsername())) {
            log.error("Пользователь с username = {} уже существует. Используйте другой username.", admin.getUsername());
            return false;
        }

        Optional<Role> adminRoleOptional = roleRepository.findByName(ROLE_ADMIN);

        if (adminRoleOptional.isEmpty()) {
            log.error("Роль {} не найдена в базе данных. Невозможно создать администратора.", ROLE_ADMIN);
            return false;
        }

        Role adminRole = adminRoleOptional.get();

        admin.setRole(adminRole);
        admin.setPassword(bCryptPasswordEncoder.encode(admin.getPassword()));
        admin.setNeedChangePass(true);

        try {
            userRepository.save(admin);
        } catch (Exception e) {
            log.error("Ошибка при сохранении администратора {}: {}", admin.getUsername(), e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }


        log.info("Администратор с username = {} успешно сохранен.", admin.getUsername());

        return true;
    }

    @Transactional
    public boolean saveUser(User user) {
        log.info("Начинаем сохранять пользователя с username = {}...", user.getUsername());

        if (checkUserName(user.getUsername())) {
            log.error("Пользователь с username = {} уже существует. Используйте другой username.", user.getUsername());
            return false;
        }

        Optional<Role> userRoleOptional = roleRepository.findByName(ROLE_VISITOR);

        if (userRoleOptional.isEmpty()) {
            log.error("Роль {} не найдена в базе данных. Невозможно создать пользователя.", ROLE_VISITOR);
            return false;
        }

        Role userRole = userRoleOptional.get();

        user.setRole(userRole);
        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));

        try {
            userRepository.save(user);
        } catch (Exception e) {
            log.error("Ошибка при сохранении пользователя {}: {}", user.getUsername(), e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }

        log.info("Пользователь с username = {} успешно сохранен.", user.getUsername());

        return true;
    }


    @Transactional
    public boolean editUser(long id,
                            String inputLastName, String inputFirstName,
                            String inputPatronymicName, String inputUsername,
                            int inputCourse, String inputFaculty) {
        Optional<User> userOptional = userRepository.findById(id);

        if (userOptional.isEmpty()) {
            log.error("Не найден пользователь с id = {}...", id);
            return false;
        }

        User user = userOptional.get();

        log.info("Начинаем сохранять изменения для пользователя с username = {}...", user.getUsername());

        if (checkUserName(inputUsername) && !user.getUsername().equals(inputUsername)) {
            log.error("Пользователь с username = {} уже существует. Используйте другой username.", user.getUsername());
            return false;
        }

        user.setLastName(inputLastName);
        user.setFirstName(inputFirstName);
        user.setPatronymicName(inputPatronymicName);
        user.setUsername(inputUsername);
        user.setCourse(inputCourse);
        user.setFaculty(inputFaculty);


        try {
            userRepository.save(user);
        } catch (Exception e) {
            log.error("Ошибка при сохранении изменений пользователя {}: {}", user.getUsername(), e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }

        log.info("Изменения для пользователя с username = {} успешно сохранены.", user.getUsername());

        return true;
    }

    @Transactional
    public boolean resetUserPassword(long id, String newPassword) {
        Optional<User> userOptional = userRepository.findById(id);

        if (userOptional.isEmpty()) {
            log.error("Не найден пользователь с id = {}...", id);
            return false;
        }

        User user = userOptional.get();

        log.info("Начинаем сохранять новый пароль для пользователя с username = {}...", user.getUsername());

        user.setPassword(bCryptPasswordEncoder.encode(newPassword));
        user.setNeedChangePass(true);

        try {
            userRepository.save(user);
        } catch (Exception e) {
            log.error("Ошибка при сохранении нового пароля пользователя {}: {}", user.getUsername(), e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }

        log.info("Новый пароль для пользователя с username = {} успешно сохранен.", user.getUsername());

        return true;
    }

    @Transactional
    public boolean deleteUser(long id){
        Optional<User> userOptional = userRepository.findById(id);

        if (userOptional.isEmpty()) {
            log.error("Не найден пользователь с id = {}...", id);
            return false;
        }

        User user = userOptional.get();

        log.info("Начинаем удаление пользователя с username = {}...", user.getUsername());

        try {
            userRepository.delete(user);
        } catch (Exception e) {
            log.error("Ошибка при удалении пользователя {}: {}", user.getUsername(), e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }

        log.info("Пользователь с username = {} успешно удален.", user.getUsername());

        return true;
    }

    public boolean checkUserName(String username) {
        return userRepository.existsByUsername(username);
    }

    public Page<User> findUsersByLastName(String lastName, boolean sortDescending, int page, int size) {
        Sort sort = Sort.by("lastName");
        if (sortDescending) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }
        Pageable pageable = PageRequest.of(page, size, sort);
        return userRepository.findByLastName(lastName, pageable);
    }

    public Page<User> findUsersByFirstName(String firstName, boolean sortDescending, int page, int size) {
        Sort sort = Sort.by("firstName");
        if (sortDescending) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }
        Pageable pageable = PageRequest.of(page, size, sort);
        return userRepository.findByFirstName(firstName, pageable);
    }

    public Page<User> findUsersByPatronymicName(String patronymicName, boolean sortDescending, int page, int size) {
        Sort sort = Sort.by("patronymicName");
        if (sortDescending) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }
        Pageable pageable = PageRequest.of(page, size, sort);
        return userRepository.findByPatronymicName(patronymicName, pageable);
    }

    public Page<User> findUsersByUsername(String username, boolean sortDescending, int page, int size) {
        Sort sort = Sort.by("username");
        if (sortDescending) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }
        Pageable pageable = PageRequest.of(page, size, sort);
        return userRepository.findByUsernameContaining(username, pageable);
    }

    public Page<User> findUsersByCourse(int course, boolean sortDescending, int page, int size) {
        Sort sort = Sort.by("course");
        if (sortDescending) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }
        Pageable pageable = PageRequest.of(page, size, sort);
        return userRepository.findByCourse(course, pageable);
    }

    public Page<User> findUsersByFaculty(String faculty, boolean sortDescending, int page, int size) {
        Sort sort = Sort.by("faculty");
        if (sortDescending) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }
        Pageable pageable = PageRequest.of(page, size, sort);
        return userRepository.findByFaculty(faculty, pageable);
    }

    public Page<User> findUsersByDateOfRegistration(LocalDate startDate, LocalDate endDate, boolean sortDescending, int page, int size) {
        Sort sort = Sort.by("dateOfRegistration");
        if (sortDescending) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }
        Pageable pageable = PageRequest.of(page, size, sort);

        // Устанавливаем значения по умолчанию для startDate и endDate
        if (startDate == null) {
            startDate = LocalDate.now(); // Если startDate не указана, берем сегодняшнюю дату
        }
        if (endDate == null) {
            endDate = LocalDate.now(); // Если endDate не указана, также берем сегодняшнюю дату
        }

        return userRepository.findByDateOfRegistrationBetween(startDate, endDate, pageable);
    }

    public Page<User> findUsersByMultipleFields(String str, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return userRepository.searchUsersByMultipleFields(str, pageable);
    }

    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);  // Получение всех пользователей с пагинацией
    }
}
