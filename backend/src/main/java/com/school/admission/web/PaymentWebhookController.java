package com.school.admission.web;

import com.school.admission.payment.PaymentWebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/payments")
@Tag(name = "Payments", description = "Gateway webhook receiver (HMAC verified, idempotent by event id)")
class PaymentWebhookController {

    private final PaymentWebhookService service;

    PaymentWebhookController(PaymentWebhookService service) {
        this.service = service;
    }

    /** Body kept as a raw String: the signature is computed over the exact bytes the gateway sent. */
    @PostMapping("/{gateway}")
    ResponseEntity<Void> receive(@PathVariable String gateway,
                                 @RequestHeader(name = "X-Signature", required = false) String signature,
                                 @RequestBody String rawBody) throws Exception {
        if (!service.signatureValid(gateway, rawBody, signature)) {
            return ResponseEntity.status(401).build();
        }
        service.handle(gateway, rawBody);
        return ResponseEntity.ok().build();
    }
}
