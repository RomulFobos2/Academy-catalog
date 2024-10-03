package com.academy.catalog.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_typeDocumentation")
public class TypeDocumentation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private CategoryDocumentation categoryDocumentation;

    @Column(length = 250)
    String type;

    public TypeDocumentation(CategoryDocumentation categoryDocumentation, String type) {
        this.categoryDocumentation = categoryDocumentation;
        this.type = type;
    }
}
