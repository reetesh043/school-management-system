package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "form_field")
@Getter @Setter @NoArgsConstructor
public class FormField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long formDefinitionId;
    private String fieldKey;
    private String label;
    private String fieldType;
    private boolean mandatory;
    private int seqNo;
    /** Comma-separated choices for SELECT fields. */
    private String options;
}
