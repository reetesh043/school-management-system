package com.school.admission.payment;

import com.school.admission.config.AppProperties;
import com.school.admission.domain.*;
import com.school.admission.engine.Actor;
import com.school.admission.engine.StageEngine;
import com.school.admission.support.Json;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * The payment webhook is the only thing allowed to mark an invoice PAID.
 *
 * <ol>
 *   <li>Verify the HMAC over the raw body.</li>
 *   <li>Insert the event keyed by (gateway, event_id); a redelivery inserts nothing and is treated as a duplicate.</li>
 *   <li>On a captured payment: mark payment SUCCESS, invoice PAID, then ask the engine for PAYMENT to ADMITTED.</li>
 * </ol>
 * All in one transaction, so a failure anywhere lets the gateway safely retry the whole event.
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    public enum Outcome { PROCESSED, DUPLICATE, IGNORED }

    private final AppProperties props;
    private final PaymentWebhookEventRepository events;
    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final StageEngine engine;
    private final Json json;

    public PaymentWebhookService(AppProperties props, PaymentWebhookEventRepository events, PaymentRepository payments,
                                 InvoiceRepository invoices, StageEngine engine, Json json) {
        this.props = props;
        this.events = events;
        this.payments = payments;
        this.invoices = invoices;
        this.engine = engine;
        this.json = json;
    }

    public boolean signatureValid(String gateway, String rawBody, String signatureHex) {
        String secret = props.payments().webhookSecrets() == null ? null : props.payments().webhookSecrets().get(gateway);
        if (secret == null || signatureHex == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] given = HexFormat.of().parseHex(signatureHex.trim().toLowerCase());
            return MessageDigest.isEqual(expected, given);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    @Transactional
    public Outcome handle(String gateway, String rawBody) throws Exception {
        JsonNode body = json.read(rawBody);
        String eventId = body.path("id").asText(null);
        if (eventId == null) {
            return Outcome.IGNORED;
        }
        if (events.existsByGatewayAndEventId(gateway, eventId)) {
            return Outcome.DUPLICATE;
        }
        PaymentWebhookEvent stored = new PaymentWebhookEvent();
        stored.setGateway(gateway);
        stored.setEventId(eventId);
        stored.setPayload(rawBody);
        events.save(stored);

        if (!"payment.captured".equals(body.path("event").asText())) {
            stored.setProcessedAt(Instant.now());
            stored.setNote("Ignored: not a capture event");
            return Outcome.IGNORED;
        }

        String orderId = body.path("payload").path("order_id").asText();
        String paymentId = body.path("payload").path("payment_id").asText();

        Payment payment = payments.findByGatewayAndGatewayOrderId(gateway, orderId).orElse(null);
        if (payment == null) {
            log.warn("Webhook {} for unknown order {}", eventId, orderId);
            stored.setProcessedAt(Instant.now());
            stored.setNote("Unknown order " + orderId);
            return Outcome.IGNORED;
        }

        if (!"SUCCESS".equals(payment.getStatus())) {
            payment.setStatus("SUCCESS");
            payment.setGatewayPaymentId(paymentId);
            payment.setSettledAt(Instant.now());
        }

        Invoice invoice = invoices.findById(payment.getInvoiceId()).orElseThrow();
        if (!"PAID".equals(invoice.getStatus())) {
            invoice.setStatus("PAID");
            invoice.setPaidAt(Instant.now());
        }

        engine.transition(invoice.getApplicationId(), "ADMITTED", Actor.SYSTEM,
                "Payment captured (" + paymentId + ")", null);

        stored.setProcessedAt(Instant.now());
        stored.setNote("Processed");
        return Outcome.PROCESSED;
    }
}
