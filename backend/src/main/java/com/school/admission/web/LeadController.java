package com.school.admission.web;

import com.school.admission.domain.Lead;
import com.school.admission.domain.LeadFollowup;
import com.school.admission.service.LeadService;
import com.school.admission.web.dto.Dtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Leads", description = "Enquiry capture, assignment, follow-ups and conversion to an application")
class LeadController {

    private final LeadService leadService;
    private final ApplicationResponses applicationResponses;

    LeadController(LeadService leadService, ApplicationResponses applicationResponses) {
        this.leadService = leadService;
        this.applicationResponses = applicationResponses;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE','COUNSELLOR','ADMISSION_COMMITTEE')")
    List<LeadResponse> list() {
        return leadService.all().stream().map(LeadController::toResponse).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE','COUNSELLOR')")
    ResponseEntity<LeadResponse> create(@Valid @RequestBody EnquiryRequest req) {
        Lead lead = leadService.capture(req.guardianName(), req.phone(), req.email(), req.childName(),
                req.childDob(), req.classCode(), req.source(), req.consent());
        return ResponseEntity.status(201).body(toResponse(lead));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE')")
    LeadResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest req) {
        return toResponse(leadService.assign(id, req.userId()));
    }

    @PostMapping("/{id}/followups")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE','COUNSELLOR')")
    ResponseEntity<Void> followup(@PathVariable Long id, @Valid @RequestBody FollowupRequest req) {
        LeadFollowup f = leadService.scheduleFollowup(id, req.dueAt(), req.note(), null);
        return ResponseEntity.created(java.net.URI.create("/api/v1/leads/" + id + "/followups/" + f.getId())).build();
    }

    @PostMapping("/{id}/lost")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE','COUNSELLOR')")
    ResponseEntity<Void> markLost(@PathVariable Long id, @RequestBody(required = false) String reason) {
        leadService.markLost(id, reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE','COUNSELLOR')")
    ResponseEntity<ApplicationResponse> convert(@PathVariable Long id, @RequestParam String academicYear) {
        var app = leadService.convert(id, academicYear);
        return ResponseEntity.status(201).body(applicationResponses.toResponse(app));
    }

    static LeadResponse toResponse(Lead l) {
        return new LeadResponse(l.getId(), l.getGuardianName(), l.getPhone(), l.getEmail(), l.getChildName(),
                l.getChildDob(), l.getClassCode(), l.getSource(), l.getStatus(), l.getAssignedToId(),
                l.getNextFollowUpAt(), l.getCreatedAt());
    }
}
