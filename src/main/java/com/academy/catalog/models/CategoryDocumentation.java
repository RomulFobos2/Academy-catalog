package com.academy.catalog.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_categoryDocumentation")
public class CategoryDocumentation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 250)
    String category;

    public CategoryDocumentation(String category) {
        this.category = category;
    }
}
