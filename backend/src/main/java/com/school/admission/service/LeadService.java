package com.school.admission.service;

import com.school.admission.domain.*;
import com.school.admission.support.BusinessRuleException;
import com.school.admission.support.Phones;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Enquiry capture, deduplication, assignment, follow-ups, and converting a lead into an application. */
@Service
public class LeadService {

    private static final Set<String> CLOSED = Set.of("CONVERTED", "LOST");

    private final LeadRepository leads;
    private final LeadFollowupRepository followups;
    private final ApplicationService applicationService;

    public LeadService(LeadRepository leads, LeadFollowupRepository followups, ApplicationService applicationService) {
        this.leads = leads;
        this.followups = followups;
        this.applicationService = applicationService;
    }

    @Transactional
    public Lead capture(String guardianName, String phone, String email, String childName, java.time.LocalDate childDob,
                        String classCode, String source, boolean consent) {
        String normalized = Phones.normalize(phone);
        if (childDob != null) {
            Lead existing = leads.findFirstByPhoneAndChildDob(normalized, childDob).orElse(null);
            if (existing != null && !CLOSED.contains(existing.getStatus())) {
                return existing;   // same guardian phone + child DOB: treat as the same enquiry
            }
        }
        Lead lead = new Lead();
        lead.setGuardianName(guardianName);
        lead.setPhone(normalized);
        lead.setEmail(email);
        lead.setChildName(childName);
        lead.setChildDob(childDob);
        lead.setClassCode(classCode);
        lead.setSource(source);
        lead.setConsent(consent);
        lead.setStatus("NEW");
        return leads.save(lead);
    }

    @Transactional
    public Lead assign(Long leadId, Long userId) {
        Lead lead = get(leadId);
        lead.setAssignedToId(userId);
        if ("NEW".equals(lead.getStatus())) {
            lead.setStatus("CONTACTED");
        }
        return lead;
    }

    @Transactional
    public LeadFollowup scheduleFollowup(Long leadId, Instant dueAt, String note, Long createdBy) {
        Lead lead = get(leadId);
        lead.setNextFollowUpAt(dueAt);
        LeadFollowup f = new LeadFollowup();
        f.setLeadId(leadId);
        f.setDueAt(dueAt);
        f.setNote(note);
        f.setCreatedById(createdBy);
        return followups.save(f);
    }

    @Transactional
    public void markLost(Long leadId, String reason) {
        Lead lead = get(leadId);
        lead.setStatus("LOST");
        lead.setLostReason(reason);
    }

    /** Starts an ENQUIRY-stage application from a lead and marks the lead CONVERTED. */
    @Transactional
    public AdmissionApplication convert(Long leadId, String academicYearLabel) {
        Lead lead = get(leadId);
        if ("CONVERTED".equals(lead.getStatus())) {
            throw new BusinessRuleException("This lead has already been converted to an application.");
        }
        if (lead.getClassCode() == null) {
            throw new BusinessRuleException("Add the class the family is interested in before converting.");
        }
        AdmissionApplication app = applicationService.startDraft(academicYearLabel, lead.getClassCode(), leadId);
        app.setChildName(lead.getChildName());
        app.setChildDob(lead.getChildDob());
        app.setGuardianName(lead.getGuardianName());
        app.setGuardianPhone(lead.getPhone());
        app.setGuardianEmail(lead.getEmail());
        lead.setStatus("CONVERTED");
        return app;
    }

    @Transactional(readOnly = true)
    public List<Lead> forCounsellor(Long userId) {
        return leads.findByAssignedToId(userId);
    }

    @Transactional(readOnly = true)
    public List<Lead> all() {
        return leads.findAll();
    }

    @Transactional(readOnly = true)
    public List<LeadFollowup> followupsFor(Long leadId) {
        return followups.findByLeadIdOrderByDueAt(leadId);
    }

    private Lead get(Long id) {
        return leads.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("Lead not found"));
    }
}
