package com.academy.catalog.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_documentation")
public class Documentation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "type_id")
    private TypeDocumentation typeDocumentation;

    @NotNull
    @Column(length = 500)
    private String filePath;

    public Documentation(TypeDocumentation typeDocumentation, String filePath) {
        this.typeDocumentation = typeDocumentation;
        this.filePath = filePath;
    }
}
