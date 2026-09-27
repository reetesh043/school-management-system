package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stage_definition")
@Getter @Setter @NoArgsConstructor
public class StageDefinition {

    public enum Category { OPEN, HOLD, TERMINAL_SUCCESS, TERMINAL_FAIL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long workflowId;
    private String code;
    private String name;
    private int seqNo;

    @Enumerated(EnumType.STRING)
    private Category category;

    /** Stages that park an application instead of moving it forward. */
    public boolean isSideStage() {
        return category == Category.HOLD || category == Category.TERMINAL_FAIL;
    }
}
