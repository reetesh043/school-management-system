package com.school.admission.web;

import com.school.admission.domain.Lead;
import com.school.admission.service.LeadService;
import com.school.admission.web.dto.Dtos.EnquiryRequest;
import com.school.admission.web.dto.Dtos.LeadResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Unauthenticated endpoint for the public enquiry form on the school website. */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public", description = "Unauthenticated website enquiry form")
class PublicController {

    private final LeadService leadService;

    PublicController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping("/enquiries")
    ResponseEntity<LeadResponse> submit(@Valid @RequestBody EnquiryRequest req) {
        Lead lead = leadService.capture(req.guardianName(), req.phone(), req.email(), req.childName(),
                req.childDob(), req.classCode(), req.source(), req.consent());
        return ResponseEntity.status(201).body(LeadController.toResponse(lead));
    }
}
