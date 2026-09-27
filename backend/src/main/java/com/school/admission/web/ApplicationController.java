package com.school.admission.web;

import com.school.admission.domain.AdmissionApplication;
import com.school.admission.domain.AdmissionApplicationRepository;
import com.school.admission.domain.StageDefinition;
import com.school.admission.domain.StageDefinitionRepository;
import com.school.admission.engine.StageEngine;
import com.school.admission.security.CurrentUser;
import com.school.admission.service.ApplicationService;
import com.school.admission.web.dto.Dtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications", description = "Application drafts, submission, stage transitions and history")
class ApplicationController {

    private final ApplicationService applicationService;
    private final AdmissionApplicationRepository applications;
    private final StageDefinitionRepository stages;
    private final StageEngine engine;
    private final CurrentUser currentUser;
    private final ApplicationResponses responses;

    ApplicationController(ApplicationService applicationService, AdmissionApplicationRepository applications,
                          StageDefinitionRepository stages, StageEngine engine, CurrentUser currentUser,
                          ApplicationResponses responses) {
        this.applicationService = applicationService;
        this.applications = applications;
        this.stages = stages;
        this.engine = engine;
        this.currentUser = currentUser;
        this.responses = responses;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    List<ApplicationResponse> list(Authentication auth, @RequestParam(required = false) String stage) {
        var user = currentUser.user(auth);
        boolean guardianOnly = currentUser.isGuardianOnly(auth);
        return applications.findAll().stream()
                .filter(a -> !guardianOnly || user.getId().equals(a.getGuardianUserId()))
                .filter(a -> stage == null || stageCode(a).equals(stage))
                .map(responses::toResponse)
                .toList();
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApplicationResponse> start(Authentication auth, @Valid @RequestBody StartApplicationRequest req) {
        var user = currentUser.user(auth);
        var app = applicationService.startDraftForGuardian(req.academicYear(), req.classCode(), user.getId());
        return ResponseEntity.status(201).body(responses.toResponse(app));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    ApplicationResponse get(@PathVariable Long id, Authentication auth) {
        AdmissionApplication app = applicationService.get(id);
        assertVisible(app, auth);
        return responses.toResponse(app);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    ApplicationResponse saveDraft(@PathVariable Long id, Authentication auth,
                                  @RequestHeader(name = "If-Match", required = false) Long ifMatch,
                                  @RequestBody SaveDraftRequest req) {
        AdmissionApplication existing = applicationService.get(id);
        assertVisible(existing, auth);
        AdmissionApplication app = applicationService.saveDraft(id, ifMatch, req.childName(), req.childDob(), req.gender(),
                req.previousSchool(), req.guardianName(), req.guardianPhone(), req.guardianEmail(), req.responses());
        return responses.toResponse(app);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    TransitionResultResponse submit(@PathVariable Long id, Authentication auth) {
        AdmissionApplication app = applicationService.get(id);
        assertVisible(app, auth);
        var r = applicationService.submit(id, currentUser.actor(auth));
        return new TransitionResultResponse(r.applicationId(), r.fromStage(), r.toStage(), r.version(), r.actionsRun());
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("isAuthenticated()")
    TransitionResultResponse transition(@PathVariable Long id, Authentication auth, @Valid @RequestBody TransitionRequest req) {
        AdmissionApplication app = applicationService.get(id);
        assertVisible(app, auth);
        var r = engine.transition(id, req.toStage(), currentUser.actor(auth), req.reason(), req.expectedVersion());
        return new TransitionResultResponse(r.applicationId(), r.fromStage(), r.toStage(), r.version(), r.actionsRun());
    }

    @GetMapping("/{id}/transitions/available")
    @PreAuthorize("isAuthenticated()")
    List<TransitionOptionResponse> available(@PathVariable Long id, Authentication auth) {
        AdmissionApplication app = applicationService.get(id);
        assertVisible(app, auth);
        return engine.available(id, currentUser.actor(auth)).stream()
                .map(o -> new TransitionOptionResponse(o.toStage(), o.toStageName(), o.roleAllowed(),
                        o.guards().stream().map(g -> new GuardStatusResponse(g.key(), g.description(), g.passed(), g.message())).toList()))
                .toList();
    }

    private void assertVisible(AdmissionApplication app, Authentication auth) {
        if (currentUser.isGuardianOnly(auth) && !currentUser.user(auth).getId().equals(app.getGuardianUserId())) {
            throw new NoSuchElementException("Application not found");
        }
    }

    private String stageCode(AdmissionApplication a) {
        return stages.findById(a.getStageId()).map(StageDefinition::getCode).orElse("?");
    }
}
