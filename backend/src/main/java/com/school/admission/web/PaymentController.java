package com.school.admission.web;

import com.school.admission.domain.Invoice;
import com.school.admission.domain.InvoiceRepository;
import com.school.admission.payment.PaymentInitiationService;
import com.school.admission.web.dto.Dtos.CreatePaymentRequest;
import com.school.admission.web.dto.Dtos.InvoiceResponse;
import com.school.admission.web.dto.Dtos.PaymentOrderResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Payments", description = "Invoices and gateway orders; the invoice becomes PAID only via the webhook")
class PaymentController {

    private final InvoiceRepository invoices;
    private final PaymentInitiationService initiation;

    PaymentController(InvoiceRepository invoices, PaymentInitiationService initiation) {
        this.invoices = invoices;
        this.initiation = initiation;
    }

    @GetMapping("/api/v1/applications/{id}/invoices")
    @PreAuthorize("isAuthenticated()")
    List<InvoiceResponse> invoicesFor(@PathVariable Long id) {
        return invoices.findByApplicationIdOrderByIdDesc(id).stream().map(PaymentController::toResponse).toList();
    }

    @PostMapping("/api/v1/invoices/{invoiceId}/payments")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<PaymentOrderResponse> pay(@PathVariable Long invoiceId,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @RequestBody(required = false) CreatePaymentRequest req) {
        var order = initiation.createOrder(invoiceId, idempotencyKey, req == null ? null : req.method());
        return ResponseEntity.status(201).body(new PaymentOrderResponse(
                order.paymentId(), order.gateway(), order.gatewayOrderId(), order.amount(), order.currency()));
    }

    private static InvoiceResponse toResponse(Invoice i) {
        return new InvoiceResponse(i.getId(), i.getInvoiceNo(), i.getAmount(), i.getCurrency(), i.getStatus(), i.getDueAt(), i.getPaidAt());
    }
}
