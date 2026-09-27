package com.school.admission.web;

import com.school.admission.domain.AcademicYear;
import com.school.admission.domain.AcademicYearRepository;
import com.school.admission.domain.ClassConfig;
import com.school.admission.domain.ClassConfigRepository;
import com.school.admission.domain.Workflow;
import com.school.admission.domain.WorkflowRepository;
import com.school.admission.service.SeatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/classes")
@Tag(name = "Forms and config", description = "Classes with age rules, seats and fees for an academic year")
class CatalogController {

    public record ClassRow(Long id, String classCode, String displayName, Integer minAgeYears, Integer maxAgeYears,
                           int seatsTotal, int seatsUsed, int seatsLeft, BigDecimal passMark,
                           BigDecimal admissionFee, int feeDueDays) { }

    public record ClassRequest(String academicYear, String classCode, String displayName,
                               Integer minAgeYears, Integer maxAgeYears, Integer seatsTotal,
                               BigDecimal passMark, BigDecimal admissionFee, Integer feeDueDays) { }

    private final AcademicYearRepository years;
    private final ClassConfigRepository classes;
    private final WorkflowRepository workflows;
    private final SeatService seats;

    CatalogController(AcademicYearRepository years, ClassConfigRepository classes,
                      WorkflowRepository workflows, SeatService seats) {
        this.years = years;
        this.classes = classes;
        this.workflows = workflows;
        this.seats = seats;
    }

    @GetMapping
    List<ClassRow> list(@RequestParam String academicYear) {
        AcademicYear year = year(academicYear);
        return classes.findByAcademicYearIdOrderById(year.getId()).stream().map(this::row).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    ClassRow create(@RequestBody ClassRequest request) {
        AcademicYear year = year(request.academicYear());
        String code = normalizeCode(request.classCode());
        String name = clean(request.displayName());
        if (name.isBlank()) throw new IllegalArgumentException("Class name is required");
        if (classes.findByAcademicYearIdAndClassCode(year.getId(), code).isPresent())
            throw new IllegalArgumentException("Class code '" + code + "' already exists for " + year.getLabel());

        validateAges(request.minAgeYears(), request.maxAgeYears());
        int capacity = request.seatsTotal() == null ? 0 : request.seatsTotal();
        if (capacity < 0) throw new IllegalArgumentException("Seat capacity cannot be negative");

        Workflow workflow = workflows.findFirstByStatusOrderByIdDesc("ACTIVE")
                .orElseThrow(() -> new IllegalStateException("No active admission workflow is configured"));

        ClassConfig c = new ClassConfig();
        c.setAcademicYearId(year.getId());
        c.setClassCode(code);
        c.setDisplayName(name);
        c.setMinAgeYears(request.minAgeYears());
        c.setMaxAgeYears(request.maxAgeYears());
        c.setSeatsTotal(capacity);
        c.setSeatsPreFilled(0);
        c.setPassMark(request.passMark() == null ? new BigDecimal("50") : request.passMark());
        c.setAdmissionFee(request.admissionFee() == null ? BigDecimal.ZERO : request.admissionFee());
        c.setFeeDueDays(request.feeDueDays() == null ? 5 : request.feeDueDays());
        c.setWorkflowId(workflow.getId());
        validateMoney(c.getPassMark(), c.getAdmissionFee(), c.getFeeDueDays());
        return row(classes.save(c));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    ClassRow update(@PathVariable Long id, @RequestBody ClassRequest request) {
        ClassConfig c = classes.findById(id).orElseThrow(() -> new NoSuchElementException("Class not found"));
        String name = clean(request.displayName());
        if (name.isBlank()) throw new IllegalArgumentException("Class name is required");
        validateAges(request.minAgeYears(), request.maxAgeYears());

        int used = c.getSeatsTotal() - seats.seatsLeft(c.getId());
        int capacity = request.seatsTotal() == null ? c.getSeatsTotal() : request.seatsTotal();
        if (capacity < used)
            throw new IllegalArgumentException("Capacity cannot be reduced below " + used + " used seat" + (used == 1 ? "" : "s"));

        c.setDisplayName(name);
        c.setMinAgeYears(request.minAgeYears());
        c.setMaxAgeYears(request.maxAgeYears());
        c.setSeatsTotal(capacity);
        if (request.passMark() != null) c.setPassMark(request.passMark());
        if (request.admissionFee() != null) c.setAdmissionFee(request.admissionFee());
        if (request.feeDueDays() != null) c.setFeeDueDays(request.feeDueDays());
        validateMoney(c.getPassMark(), c.getAdmissionFee(), c.getFeeDueDays());
        return row(classes.save(c));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    void delete(@PathVariable Long id) {
        ClassConfig c = classes.findById(id).orElseThrow(() -> new NoSuchElementException("Class not found"));
        int used = c.getSeatsTotal() - seats.seatsLeft(c.getId());
        if (used > 0) throw new IllegalArgumentException("This class has used seats and cannot be deleted. Set capacity or stop future admissions instead.");
        try {
            classes.delete(c);
            classes.flush();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("This class is already referenced by school records and cannot be deleted. Keep it and edit its capacity instead.");
        }
    }

    private ClassRow row(ClassConfig c) {
        int left = seats.seatsLeft(c.getId());
        int used = Math.max(0, c.getSeatsTotal() - left);
        return new ClassRow(c.getId(), c.getClassCode(), c.getDisplayName(), c.getMinAgeYears(), c.getMaxAgeYears(),
                c.getSeatsTotal(), used, left, c.getPassMark(), c.getAdmissionFee(), c.getFeeDueDays());
    }

    private AcademicYear year(String label) {
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Academic year is required");
        return years.findByLabel(label).orElseThrow(() -> new NoSuchElementException("Unknown academic year " + label));
    }

    private String normalizeCode(String raw) {
        String code = clean(raw).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
        if (code.isBlank()) throw new IllegalArgumentException("Class code is required");
        if (code.length() > 20) throw new IllegalArgumentException("Class code must be 20 characters or fewer");
        return code;
    }

    private void validateAges(Integer min, Integer max) {
        if (min != null && min < 0 || max != null && max < 0) throw new IllegalArgumentException("Age cannot be negative");
        if (min != null && max != null && max < min) throw new IllegalArgumentException("Maximum age cannot be below minimum age");
    }

    private void validateMoney(BigDecimal passMark, BigDecimal admissionFee, int feeDueDays) {
        if (passMark == null || passMark.signum() < 0 || passMark.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Pass mark must be between 0 and 100");
        if (admissionFee == null || admissionFee.signum() < 0) throw new IllegalArgumentException("Admission fee cannot be negative");
        if (feeDueDays < 0) throw new IllegalArgumentException("Fee due days cannot be negative");
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
}
