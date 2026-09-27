package com.school.admission.devtools;

import com.school.admission.config.AppProperties;
import com.school.admission.domain.Payment;
import com.school.admission.domain.PaymentRepository;
import com.school.admission.payment.HmacSigner;
import com.school.admission.payment.PaymentWebhookService;
import com.school.admission.support.BusinessRuleException;
import com.school.admission.support.Json;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Stands in for a real payment gateway so the PAYMENT -> ADMITTED path can be exercised without one.
 * Guarded by admission.dev-tools.enabled; turn it off (or delete this class) outside development.
 */
@RestController
@RequestMapping("/api/v1/dev/payments")
@Tag(name = "Payments", description = "DEV ONLY: simulates a gateway capturing a payment and firing its webhook")
class DevPaymentController {

    private final PaymentRepository payments;
    private final PaymentWebhookService webhookService;
    private final AppProperties props;
    private final Json json;

    DevPaymentController(PaymentRepository payments, PaymentWebhookService webhookService, AppProperties props, Json json) {
        this.payments = payments;
        this.webhookService = webhookService;
        this.props = props;
        this.json = json;
    }

    public record CaptureResult(String outcome) { }

    /** Marks the given payment as captured by sending ourselves a correctly signed webhook. */
    @PostMapping("/{paymentId}/capture")
    @PreAuthorize("isAuthenticated()")
    CaptureResult capture(@PathVariable Long paymentId) throws Exception {
        if (!props.devTools().enabled()) {
            throw new BusinessRuleException("Developer payment simulation is turned off.");
        }
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Payment not found"));

        String body = json.write(Map.of(
                "id", "evt_" + UUID.randomUUID(),
                "event", "payment.captured",
                "payload", Map.of("order_id", payment.getGatewayOrderId(), "payment_id", "pay_" + UUID.randomUUID())));

        String secret = props.payments().webhookSecrets().get(payment.getGateway());
        String signature = HmacSigner.sign(secret, body);
        PaymentWebhookService.Outcome outcome = webhookService.handle(payment.getGateway(), body);
        return new CaptureResult(outcome.name() + " (signature " + signature.substring(0, 8) + "...)");
    }
}
