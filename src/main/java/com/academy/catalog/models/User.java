package com.academy.catalog.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Collections;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_user")
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    @Column(length = 50)
    private String lastName; //Фамилия
    @NotNull
    @Column(length = 50)
    private String firstName; //Имя
    @Column(length = 50)
    private String patronymicName; //Отчество

    @Column(unique = true, length = 50)
    private String username;
    @NotNull
    @Column(unique = true, length = 200)
    private String password;
    @Transient
    private String fullName;
    private boolean needChangePass = true;

    private int course; //курс студента
    @Column(length = 100)
    private String faculty; //факультет

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate dateOfRegistration;

    public String getFullName() {
        if(role.getName().equals("ROLE_ADMIN")){
            return "Администратор";
        }
        return lastName + " " + firstName + " " + patronymicName;
    }

    public User(String lastName, String firstName, String patronymicName, String username, String password,
                int course, String faculty) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.patronymicName = patronymicName;
        this.username = username;
        this.password = password;
        this.course = course;
        this.faculty = faculty;
        this.dateOfRegistration = LocalDate.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(role.getName()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", fullName='" + getFullName() + '\'' +
                ", role=" + role +
                '}';
    }
}