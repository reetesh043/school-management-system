package com.school.admission.service;

import com.school.admission.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Reporting aggregations for the admissions dashboard and downloadable reports. */
@Service
public class ReportingService {

    private final LeadRepository leads;
    private final AdmissionApplicationRepository applications;
    private final ClassConfigRepository classes;
    private final StageDefinitionRepository stages;
    private final StageHistoryRepository history;
    private final SeatAllocationRepository seatAllocations;

    public ReportingService(LeadRepository leads, AdmissionApplicationRepository applications,
                            ClassConfigRepository classes, StageDefinitionRepository stages, StageHistoryRepository history,
                            SeatAllocationRepository seatAllocations) {
        this.leads = leads;
        this.applications = applications;
        this.classes = classes;
        this.stages = stages;
        this.history = history;
        this.seatAllocations = seatAllocations;
    }

    public record SeatRow(String classCode, String displayName, int seatsTotal, int seatsUsed, int seatsLeft) { }
    public record SourceRow(String source, long enquiries, long applications, long admitted) { }
    public record FunnelRow(String stage, long reached, double conversionFromEnquiry) { }
    public record Overview(long totalLeads, long newLeads, long convertedLeads, long lostLeads,
                           long overdueFollowUps, long totalApplications, long inProgress,
                           long admitted, long waitlisted, long rejected, long paymentPending,
                           int seatsTotal, int seatsUsed, int seatsLeft,
                           double leadToApplicationRate, double applicationToAdmissionRate,
                           double seatUtilisationRate) { }
    public record StageRow(String stage, long count) { }
    public record ClassDemandRow(String classCode, String displayName, long enquiries, long applications,
                                 long admitted, int seatsTotal, int seatsLeft, double occupancyRate) { }

    @Transactional(readOnly = true)
    public List<SeatRow> seatAvailability(Long academicYearId) {
        return classes.findByAcademicYearIdOrderById(academicYearId).stream()
                .map(c -> {
                    int allocated = (int) seatAllocations.countByClassConfigIdAndStatusIn(c.getId(), List.of(SeatAllocation.HELD, SeatAllocation.CONFIRMED));
                    int used = c.getSeatsPreFilled() + allocated;
                    return new SeatRow(c.getClassCode(), c.getDisplayName(), c.getSeatsTotal(), used, Math.max(0, c.getSeatsTotal() - used));
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<SourceRow> sourceConversion() {
        Map<String, List<Lead>> bySource = leads.findAll().stream().collect(Collectors.groupingBy(Lead::getSource));
        List<SourceRow> rows = new ArrayList<>();
        for (var e : bySource.entrySet()) {
            long enquiries = e.getValue().size();
            long converted = e.getValue().stream().filter(l -> "CONVERTED".equals(l.getStatus())).count();
            long admitted = e.getValue().stream()
                    .map(l -> applications.findFirstByLeadId(l.getId()).orElse(null))
                    .filter(a -> a != null && a.getAdmissionNo() != null)
                    .count();
            rows.add(new SourceRow(e.getKey(), enquiries, converted, admitted));
        }
        rows.sort(Comparator.comparing(SourceRow::source));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<FunnelRow> funnel(Long classConfigId) {
        ClassConfig classConfig = classes.findById(classConfigId).orElseThrow();
        List<StageDefinition> ordered = stages.findByWorkflowIdOrderBySeqNo(classConfig.getWorkflowId()).stream()
                .filter(s -> s.getCategory() != StageDefinition.Category.HOLD)
                .toList();
        Map<Long, Long> reachedByStage = new HashMap<>();
        List<AdmissionApplication> classApps = applications.findAll().stream()
                .filter(a -> Objects.equals(a.getClassConfigId(), classConfigId)).toList();
        for (AdmissionApplication app : classApps) {
            Set<Long> reached = history.findByApplicationIdOrderByIdAsc(app.getId()).stream()
                    .map(StageHistory::getToStageId).collect(Collectors.toSet());
            reached.add(app.getStageId());
            reached.forEach(stageId -> reachedByStage.merge(stageId, 1L, Long::sum));
        }
        long enquiryReached = ordered.isEmpty() ? 0 : reachedByStage.getOrDefault(ordered.get(0).getId(), 0L);
        List<FunnelRow> rows = new ArrayList<>();
        for (StageDefinition stage : ordered) {
            long reached = reachedByStage.getOrDefault(stage.getId(), 0L);
            double pct = enquiryReached == 0 ? 0 : (double) reached / enquiryReached;
            rows.add(new FunnelRow(stage.getCode(), reached, Math.round(pct * 1000) / 1000.0));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Overview overview(Long academicYearId) {
        List<Lead> allLeads = leads.findAll();
        List<AdmissionApplication> allApps = applications.findAll().stream()
                .filter(a -> Objects.equals(a.getAcademicYearId(), academicYearId)).toList();
        Map<Long, String> stageCodes = stages.findAll().stream()
                .collect(Collectors.toMap(StageDefinition::getId, StageDefinition::getCode));

        long newLeads = allLeads.stream().filter(l -> "NEW".equals(l.getStatus())).count();
        long converted = allLeads.stream().filter(l -> "CONVERTED".equals(l.getStatus())).count();
        long lost = allLeads.stream().filter(l -> "LOST".equals(l.getStatus())).count();
        long overdue = allLeads.stream().filter(l -> l.getNextFollowUpAt() != null && l.getNextFollowUpAt().isBefore(Instant.now()))
                .filter(l -> !"LOST".equals(l.getStatus()) && !"CONVERTED".equals(l.getStatus())).count();
        long admitted = allApps.stream().filter(a -> a.getAdmissionNo() != null).count();
        long waitlisted = countStage(allApps, stageCodes, "WAITLISTED");
        long rejected = countStage(allApps, stageCodes, "REJECTED");
        long payment = countStage(allApps, stageCodes, "PAYMENT");
        long inProgress = allApps.size() - admitted - rejected;

        List<ClassConfig> yearClasses = classes.findByAcademicYearIdOrderById(academicYearId);
        int totalSeats = yearClasses.stream().mapToInt(ClassConfig::getSeatsTotal).sum();
        int usedSeats = yearClasses.stream().mapToInt(c -> c.getSeatsPreFilled() +
                (int) seatAllocations.countByClassConfigIdAndStatusIn(c.getId(), List.of(SeatAllocation.HELD, SeatAllocation.CONFIRMED))).sum();
        int leftSeats = Math.max(0, totalSeats - usedSeats);

        return new Overview(allLeads.size(), newLeads, converted, lost, overdue, allApps.size(), inProgress,
                admitted, waitlisted, rejected, payment, totalSeats, usedSeats, leftSeats,
                rate(converted, allLeads.size()), rate(admitted, allApps.size()), rate(usedSeats, totalSeats));
    }

    @Transactional(readOnly = true)
    public List<StageRow> stageDistribution(Long academicYearId) {
        Map<Long, String> stageCodes = stages.findAll().stream()
                .collect(Collectors.toMap(StageDefinition::getId, StageDefinition::getCode));
        Map<String, Long> counts = applications.findAll().stream()
                .filter(a -> Objects.equals(a.getAcademicYearId(), academicYearId))
                .collect(Collectors.groupingBy(a -> stageCodes.getOrDefault(a.getStageId(), "UNKNOWN"), Collectors.counting()));
        List<String> order = List.of("ENQUIRY", "APPLICATION", "REGISTRATION", "INITIATED", "PAYMENT", "ADMITTED", "WAITLISTED", "REJECTED");
        return order.stream().map(s -> new StageRow(s, counts.getOrDefault(s, 0L))).toList();
    }

    @Transactional(readOnly = true)
    public List<ClassDemandRow> classDemand(Long academicYearId) {
        List<Lead> allLeads = leads.findAll();
        List<AdmissionApplication> allApps = applications.findAll().stream()
                .filter(a -> Objects.equals(a.getAcademicYearId(), academicYearId)).toList();
        return classes.findByAcademicYearIdOrderById(academicYearId).stream().map(c -> {
            long enquiryCount = allLeads.stream().filter(l -> c.getClassCode().equals(l.getClassCode())).count();
            long appCount = allApps.stream().filter(a -> Objects.equals(a.getClassConfigId(), c.getId())).count();
            long admittedCount = allApps.stream().filter(a -> Objects.equals(a.getClassConfigId(), c.getId()) && a.getAdmissionNo() != null).count();
            int allocated = (int) seatAllocations.countByClassConfigIdAndStatusIn(c.getId(), List.of(SeatAllocation.HELD, SeatAllocation.CONFIRMED));
            int used = c.getSeatsPreFilled() + allocated;
            int left = Math.max(0, c.getSeatsTotal() - used);
            return new ClassDemandRow(c.getClassCode(), c.getDisplayName(), enquiryCount, appCount, admittedCount,
                    c.getSeatsTotal(), left, rate(used, c.getSeatsTotal()));
        }).toList();
    }

    private long countStage(List<AdmissionApplication> apps, Map<Long, String> stageCodes, String stage) {
        return apps.stream().filter(a -> stage.equals(stageCodes.get(a.getStageId()))).count();
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round(((double) numerator / denominator) * 1000) / 10.0;
    }
}
