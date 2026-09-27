package com.school.admission.web;

import com.school.admission.domain.AcademicYearRepository;
import com.school.admission.domain.ClassConfigRepository;
import com.school.admission.service.ReportingService;
import com.school.admission.web.dto.Dtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Admissions dashboard, capacity, conversion and funnel reporting")
@PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','ADMISSION_COMMITTEE','ACCOUNTS','FRONT_OFFICE','COUNSELLOR')")
class ReportController {

    private final ReportingService reporting;
    private final AcademicYearRepository years;
    private final ClassConfigRepository classes;

    ReportController(ReportingService reporting, AcademicYearRepository years, ClassConfigRepository classes) {
        this.reporting = reporting;
        this.years = years;
        this.classes = classes;
    }

    @GetMapping("/seats")
    List<SeatRowResponse> seats(@RequestParam String academicYear) {
        Long yearId = yearId(academicYear);
        return reporting.seatAvailability(yearId).stream()
                .map(r -> new SeatRowResponse(r.classCode(), r.displayName(), r.seatsTotal(), r.seatsUsed(), r.seatsLeft()))
                .toList();
    }

    @GetMapping("/sources")
    List<SourceRowResponse> sources() {
        return reporting.sourceConversion().stream()
                .map(r -> new SourceRowResponse(r.source(), r.enquiries(), r.applications(), r.admitted()))
                .toList();
    }

    @GetMapping("/funnel")
    List<FunnelRowResponse> funnel(@RequestParam String academicYear, @RequestParam String classCode) {
        Long yearId = yearId(academicYear);
        Long classConfigId = classes.findByAcademicYearIdAndClassCode(yearId, classCode)
                .orElseThrow(() -> new NoSuchElementException("Unknown class")).getId();
        return reporting.funnel(classConfigId).stream()
                .map(r -> new FunnelRowResponse(r.stage(), r.reached(), r.conversionFromEnquiry()))
                .toList();
    }

    @GetMapping("/overview")
    OverviewResponse overview(@RequestParam String academicYear) {
        var r = reporting.overview(yearId(academicYear));
        return new OverviewResponse(r.totalLeads(), r.newLeads(), r.convertedLeads(), r.lostLeads(), r.overdueFollowUps(),
                r.totalApplications(), r.inProgress(), r.admitted(), r.waitlisted(), r.rejected(), r.paymentPending(),
                r.seatsTotal(), r.seatsUsed(), r.seatsLeft(), r.leadToApplicationRate(), r.applicationToAdmissionRate(), r.seatUtilisationRate());
    }

    @GetMapping("/stages")
    List<StageCountResponse> stages(@RequestParam String academicYear) {
        return reporting.stageDistribution(yearId(academicYear)).stream()
                .map(r -> new StageCountResponse(r.stage(), r.count())).toList();
    }

    @GetMapping("/class-demand")
    List<ClassDemandResponse> classDemand(@RequestParam String academicYear) {
        return reporting.classDemand(yearId(academicYear)).stream()
                .map(r -> new ClassDemandResponse(r.classCode(), r.displayName(), r.enquiries(), r.applications(), r.admitted(),
                        r.seatsTotal(), r.seatsLeft(), r.occupancyRate())).toList();
    }

    @GetMapping(value = "/export", produces = "text/csv")
    ResponseEntity<String> export(@RequestParam String academicYear, @RequestParam(defaultValue = "summary") String report) {
        Long yearId = yearId(academicYear);
        String csv = switch (report.toLowerCase()) {
            case "seats" -> seatsCsv(yearId);
            case "sources" -> sourcesCsv();
            case "class-demand" -> classDemandCsv(yearId);
            default -> overviewCsv(yearId, academicYear);
        };
        String filename = "admissions-" + report.toLowerCase() + "-" + academicYear + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }

    private Long yearId(String label) {
        return years.findByLabel(label).orElseThrow(() -> new NoSuchElementException("Unknown year")).getId();
    }

    private String overviewCsv(Long yearId, String academicYear) {
        var r = reporting.overview(yearId);
        return "Metric,Value\n" +
                "Academic year," + csv(academicYear) + "\n" +
                "Total leads," + r.totalLeads() + "\n" +
                "Converted leads," + r.convertedLeads() + "\n" +
                "Lost leads," + r.lostLeads() + "\n" +
                "Overdue follow-ups," + r.overdueFollowUps() + "\n" +
                "Total applications," + r.totalApplications() + "\n" +
                "Applications in progress," + r.inProgress() + "\n" +
                "Admitted," + r.admitted() + "\n" +
                "Waitlisted," + r.waitlisted() + "\n" +
                "Rejected," + r.rejected() + "\n" +
                "Payment pending," + r.paymentPending() + "\n" +
                "Seats total," + r.seatsTotal() + "\n" +
                "Seats used," + r.seatsUsed() + "\n" +
                "Seats left," + r.seatsLeft() + "\n" +
                "Lead to application rate (%)," + r.leadToApplicationRate() + "\n" +
                "Application to admission rate (%)," + r.applicationToAdmissionRate() + "\n" +
                "Seat utilisation (%)," + r.seatUtilisationRate() + "\n";
    }

    private String seatsCsv(Long yearId) {
        StringBuilder b = new StringBuilder("Class code,Class,Total seats,Used seats,Seats left\n");
        reporting.seatAvailability(yearId).forEach(r -> b.append(csv(r.classCode())).append(',').append(csv(r.displayName())).append(',')
                .append(r.seatsTotal()).append(',').append(r.seatsUsed()).append(',').append(r.seatsLeft()).append('\n'));
        return b.toString();
    }

    private String sourcesCsv() {
        StringBuilder b = new StringBuilder("Source,Enquiries,Applications,Admitted,Application conversion %,Admission conversion %\n");
        reporting.sourceConversion().forEach(r -> b.append(csv(r.source())).append(',').append(r.enquiries()).append(',')
                .append(r.applications()).append(',').append(r.admitted()).append(',')
                .append(rate(r.applications(), r.enquiries())).append(',').append(rate(r.admitted(), r.enquiries())).append('\n'));
        return b.toString();
    }

    private String classDemandCsv(Long yearId) {
        StringBuilder b = new StringBuilder("Class code,Class,Enquiries,Applications,Admitted,Total seats,Seats left,Seat utilisation %\n");
        reporting.classDemand(yearId).forEach(r -> b.append(csv(r.classCode())).append(',').append(csv(r.displayName())).append(',')
                .append(r.enquiries()).append(',').append(r.applications()).append(',').append(r.admitted()).append(',')
                .append(r.seatsTotal()).append(',').append(r.seatsLeft()).append(',').append(r.occupancyRate()).append('\n'));
        return b.toString();
    }

    private String csv(String s) { return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\""; }
    private double rate(long n, long d) { return d == 0 ? 0 : Math.round(((double)n / d) * 1000) / 10.0; }
}
