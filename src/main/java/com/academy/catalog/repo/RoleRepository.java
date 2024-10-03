package com.academy.catalog.repo;

import com.academy.catalog.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

//Класс для работы в БД с сущностью Role
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);

}
