package com.academy.catalog.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_backup")
public class Backup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 250)
    String archiveName;

    @Column(length = 500)
    String controlSumma;

    @NotNull
    @DateTimeFormat(pattern = "dd.MM.yyyy HH:mm")
    LocalDateTime createDate; //дата заказа

    public Backup(String archiveName, String controlSumma) {
        this.archiveName = archiveName;
        this.controlSumma = controlSumma;
        this.createDate = LocalDateTime.now();
    }
}
