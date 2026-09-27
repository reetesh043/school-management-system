package com.school.admission.web;

import com.school.admission.domain.*;
import com.school.admission.support.Json;
import com.school.admission.web.dto.Dtos.ApplicationResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Shared response builder so every controller that returns an application looks the same. */
@Component
public class ApplicationResponses {

    private final ClassConfigRepository classes;
    private final StageDefinitionRepository stages;
    private final Json json;

    public ApplicationResponses(ClassConfigRepository classes, StageDefinitionRepository stages, Json json) {
        this.classes = classes;
        this.stages = stages;
        this.json = json;
    }

    public ApplicationResponse toResponse(AdmissionApplication a) {
        String classCode = classes.findById(a.getClassConfigId()).map(ClassConfig::getClassCode).orElse(null);
        String stageCode = stages.findById(a.getStageId()).map(StageDefinition::getCode).orElse(null);
        Map<String, Object> responses = json.toMap(a.getResponses());
        return new ApplicationResponse(a.getId(), a.getApplicationNo(), classCode, stageCode, a.getVersion(),
                a.getAdmissionNo(), a.getChildName(), a.getChildDob(), a.getGuardianName(), a.getGuardianPhone(),
                a.getGuardianEmail(), responses, a.getSubmittedAt());
    }
}
