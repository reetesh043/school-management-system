package com.school.admission.service;

import com.school.admission.domain.*;
import com.school.admission.engine.Actor;
import com.school.admission.engine.StageEngine;
import com.school.admission.support.BusinessRuleException;
import com.school.admission.support.Json;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Draft creation, saving answers, and submission. Stage transitions themselves live in StageEngine. */
@Service
public class ApplicationService {

    private final AdmissionApplicationRepository applications;
    private final AcademicYearRepository years;
    private final ClassConfigRepository classes;
    private final StageDefinitionRepository stages;
    private final FormService formService;
    private final ApplicationNumberGenerator numbers;
    private final StageEngine engine;
    private final Json json;

    public ApplicationService(AdmissionApplicationRepository applications, AcademicYearRepository years,
                              ClassConfigRepository classes, StageDefinitionRepository stages, FormService formService,
                              ApplicationNumberGenerator numbers, StageEngine engine, Json json) {
        this.applications = applications;
        this.years = years;
        this.classes = classes;
        this.stages = stages;
        this.formService = formService;
        this.numbers = numbers;
        this.engine = engine;
        this.json = json;
    }

    @Transactional
    public AdmissionApplication startDraft(String academicYearLabel, String classCode, Long leadId) {
        AcademicYear year = years.findByLabel(academicYearLabel)
                .orElseThrow(() -> new BusinessRuleException("Unknown academic year " + academicYearLabel + "."));
        if (!year.isAdmissionsOpen()) {
            throw new BusinessRuleException("Admissions for " + academicYearLabel + " are not open yet.");
        }
        String normalizedClassCode = normalizeClassCode(classCode);
        ClassConfig cls = classes.findByAcademicYearIdAndClassCode(year.getId(), normalizedClassCode)
                .orElseThrow(() -> new BusinessRuleException("Unknown class " + classCode + " for " + academicYearLabel + "."));
        StageDefinition entry = stages.findByWorkflowIdAndCode(cls.getWorkflowId(), "ENQUIRY")
                .orElseThrow(() -> new IllegalStateException("Workflow has no ENQUIRY stage"));
        FormDefinition form = formService.currentApplicationForm();

        AdmissionApplication app = new AdmissionApplication();
        app.setApplicationNo(numbers.next(year.getStartsOn().getYear()));
        app.setAcademicYearId(year.getId());
        app.setClassConfigId(cls.getId());
        app.setWorkflowId(cls.getWorkflowId());
        app.setStageId(entry.getId());
        app.setLeadId(leadId);
        app.setFormDefinitionId(form.getId());
        app.setFormVersion(form.getVersion());
        app.setResponses("{}");
        return applications.save(app);
    }

    @Transactional
    public AdmissionApplication startDraftForGuardian(String academicYearLabel, String classCode, Long guardianUserId) {
        AdmissionApplication app = startDraft(academicYearLabel, classCode, null);
        app.setGuardianUserId(guardianUserId);
        return app;
    }

    /** Partial update with optimistic locking: the caller's If-Match version must still be current. */
    @Transactional
    public AdmissionApplication saveDraft(Long applicationId, Long expectedVersion, String childName,
                                          java.time.LocalDate childDob, String gender, String previousSchool,
                                          String guardianName, String guardianPhone, String guardianEmail,
                                          Map<String, Object> responsePatch) {
        AdmissionApplication app = get(applicationId);
        if (expectedVersion != null && !expectedVersion.equals(app.getVersion())) {
            throw new com.school.admission.engine.StaleApplicationException(expectedVersion, app.getVersion());
        }
        if (childName != null) app.setChildName(childName);
        if (childDob != null) app.setChildDob(childDob);
        if (gender != null) app.setGender(gender);
        if (previousSchool != null) app.setPreviousSchool(previousSchool);
        if (guardianName != null) app.setGuardianName(guardianName);
        if (guardianPhone != null) app.setGuardianPhone(guardianPhone);
        if (guardianEmail != null) app.setGuardianEmail(guardianEmail);
        if (responsePatch != null && !responsePatch.isEmpty()) {
            Map<String, Object> merged = json.toMap(app.getResponses());
            merged.putAll(responsePatch);
            app.setResponses(json.write(merged));
        }
        return app;
    }

    @Transactional
    public StageEngine.Result submit(Long applicationId, Actor actor) {
        AdmissionApplication app = get(applicationId);
        app.setSubmittedAt(Instant.now());
        return engine.transition(applicationId, "APPLICATION", actor, null, null);
    }

    @Transactional(readOnly = true)
    public AdmissionApplication get(Long id) {
        return applications.findById(id).orElseThrow(() -> new NoSuchElementException("Application not found"));
    }

    @Transactional(readOnly = true)
    public List<AdmissionApplication> all() {
        return applications.findAll();
    }
    private String normalizeClassCode(String classCode) {
        if (classCode == null) return null;
        String code = classCode.trim().toUpperCase();
        if (code.equals("NURSERY")) return "NUR";
        if (code.startsWith("CLASS_")) return "C" + code.substring("CLASS_".length());
        if (code.startsWith("CLASS ")) return "C" + code.substring("CLASS ".length()).trim();
        return code;
    }

}
