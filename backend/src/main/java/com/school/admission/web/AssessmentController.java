package com.school.admission.web;

import com.school.admission.domain.AdmissionApplication;
import com.school.admission.security.CurrentUser;
import com.school.admission.service.ApplicationService;
import com.school.admission.service.AssessmentService;
import com.school.admission.web.dto.Dtos.AssessmentRequest;
import com.school.admission.web.dto.Dtos.AssessmentResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/applications/{id}/assessments")
@Tag(name = "Assessment", description = "Record interaction or entrance-test scores against the class pass mark")
class AssessmentController {

    private final AssessmentService assessments;
    private final ApplicationService applications;
    private final CurrentUser currentUser;

    AssessmentController(AssessmentService assessments, ApplicationService applications, CurrentUser currentUser) {
        this.assessments = assessments;
        this.applications = applications;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','ADMISSION_COMMITTEE')")
    AssessmentResponse record(@PathVariable Long id, Authentication auth, @Valid @RequestBody AssessmentRequest req) {
        AdmissionApplication app = applications.get(id);
        var a = assessments.record(id, app.getClassConfigId(), req.kind(), req.score(), req.remarks(),
                currentUser.user(auth).getId());
        return new AssessmentResponse(a.getScore(), a.isPassed(), a.getRemarks());
    }
}
