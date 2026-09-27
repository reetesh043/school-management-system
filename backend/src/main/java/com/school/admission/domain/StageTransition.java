package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

/**
 * One allowed edge in a workflow. Roles, guards and actions are stored as comma-separated keys, so an admin
 * can rewire a workflow with data while developers add behaviour by adding a Spring bean.
 */
@Entity
@Table(name = "stage_transition")
@Getter @Setter @NoArgsConstructor
public class StageTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long workflowId;
    private Long fromStageId;
    private Long toStageId;
    private String allowedRoles;
    private String guardKeys;
    private String actionKeys;

    public List<String> roles() { return split(allowedRoles); }
    public List<String> guards() { return split(guardKeys); }
    public List<String> actions() { return split(actionKeys); }

    public static String join(List<String> keys) {
        return keys == null ? "" : String.join(",", keys.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
