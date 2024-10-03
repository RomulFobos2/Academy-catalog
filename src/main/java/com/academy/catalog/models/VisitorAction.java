package com.academy.catalog.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_visitorAction")
public class VisitorAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String visitorFullName;
    private String documentPath;
    @DateTimeFormat(pattern = "dd.MM.yyyy HH:mm")
    private LocalDateTime timeOfVisit;

    public VisitorAction(String username, String visitorFullName, String documentPath) {
        this.username = username;
        this.visitorFullName = visitorFullName;
        this.documentPath = documentPath;
        this.timeOfVisit = LocalDateTime.now();
    }
}
