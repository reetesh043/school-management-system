package com.school.admission.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request and response records for the REST API. Kept in one file since each is a few lines. */
public final class Dtos {

    private Dtos() { }

    public record EnquiryRequest(@NotBlank String guardianName, @NotBlank String phone, String email,
                                 String childName, LocalDate childDob, String classCode,
                                 @NotBlank String source, boolean consent) { }

    public record LeadResponse(Long id, String guardianName, String phone, String email, String childName,
                               LocalDate childDob, String classCode, String source, String status,
                               Long assignedToId, java.time.Instant nextFollowUpAt, java.time.Instant createdAt) { }

    public record AssignRequest(@NotNull Long userId) { }

    public record FollowupRequest(@NotNull java.time.Instant dueAt, String note) { }

    public record StartApplicationRequest(@NotBlank String academicYear, @NotBlank String classCode) { }

    public record SaveDraftRequest(String childName, LocalDate childDob, String gender, String previousSchool,
                                   String guardianName, String guardianPhone, String guardianEmail,
                                   Map<String, Object> responses) { }

    public record ApplicationResponse(Long id, String applicationNo, String classCode, String stage,
                                      Long version, String admissionNo, String childName, LocalDate childDob,
                                      String guardianName, String guardianPhone, String guardianEmail,
                                      Map<String, Object> responses, java.time.Instant submittedAt) { }

    public record TransitionRequest(@NotBlank String toStage, String reason, Long expectedVersion) { }

    public record TransitionResultResponse(Long applicationId, String fromStage, String toStage, long version, List<String> actionsRun) { }

    public record GuardStatusResponse(String key, String description, boolean passed, String message) { }

    public record TransitionOptionResponse(String toStage, String toStageName, boolean roleAllowed, List<GuardStatusResponse> guards) { }

    public record DocumentResponse(Long id, String docType, String docLabel, String fileName,
                                   String verificationStatus, String rejectReason, java.time.Instant uploadedAt) { }

    public record VerifyDocumentRequest(@NotBlank String status, String reason) { }

    public record BookSlotRequest(@NotNull Long applicationId) { }

    public record SlotResponse(Long id, String kind, java.time.Instant startsAt, java.time.Instant endsAt, int remaining) { }

    public record AssessmentRequest(@NotBlank String kind, @NotNull BigDecimal score, String remarks) { }

    public record AssessmentResponse(BigDecimal score, boolean passed, String remarks) { }

    public record InvoiceResponse(Long id, String invoiceNo, BigDecimal amount, String currency, String status,
                                  java.time.Instant dueAt, java.time.Instant paidAt) { }

    public record CreatePaymentRequest(String method) { }

    public record PaymentOrderResponse(Long paymentId, String gateway, String gatewayOrderId, BigDecimal amount, String currency) { }

    public record MessageResponse(String channel, String templateCode, String status, java.time.Instant createdAt) { }

    public record SeatRowResponse(String classCode, String displayName, int seatsTotal, int seatsUsed, int seatsLeft) { }

    public record SourceRowResponse(String source, long enquiries, long applications, long admitted) { }

    public record FunnelRowResponse(String stage, long reached, double conversionFromEnquiry) { }

    public record OverviewResponse(long totalLeads, long newLeads, long convertedLeads, long lostLeads,
                                   long overdueFollowUps, long totalApplications, long inProgress, long admitted,
                                   long waitlisted, long rejected, long paymentPending, int seatsTotal, int seatsUsed,
                                   int seatsLeft, double leadToApplicationRate, double applicationToAdmissionRate,
                                   double seatUtilisationRate) { }

    public record StageCountResponse(String stage, long count) { }

    public record ClassDemandResponse(String classCode, String displayName, long enquiries, long applications,
                                      long admitted, int seatsTotal, int seatsLeft, double occupancyRate) { }
}
