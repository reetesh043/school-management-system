package com.school.admission.payment;

import com.school.admission.config.AppProperties;
import com.school.admission.domain.Invoice;
import com.school.admission.domain.InvoiceRepository;
import com.school.admission.domain.Payment;
import com.school.admission.domain.PaymentRepository;
import com.school.admission.support.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates a gateway order for an invoice. There is no real payment gateway wired in; MockPaymentGateway
 * stands in for one so the whole PAYMENT -> ADMITTED path can be exercised end to end (see /api/v1/dev/payments).
 */
@Service
public class PaymentInitiationService {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final AppProperties props;

    public PaymentInitiationService(InvoiceRepository invoices, PaymentRepository payments, AppProperties props) {
        this.invoices = invoices;
        this.payments = payments;
        this.props = props;
    }

    public record Order(Long paymentId, String gateway, String gatewayOrderId, java.math.BigDecimal amount, String currency) { }

    @Transactional
    public Order createOrder(Long invoiceId, String idempotencyKey, String method) {
        payments.findByIdempotencyKey(idempotencyKey).ifPresent(existing -> {
            throw new BusinessRuleException("This payment was already started (idempotency key already used).");
        });
        Invoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Invoice not found"));
        if ("PAID".equals(invoice.getStatus())) {
            throw new BusinessRuleException("This invoice is already paid.");
        }
        String gateway = props.payments().defaultGateway();
        Payment payment = new Payment();
        payment.setInvoiceId(invoiceId);
        payment.setGateway(gateway);
        payment.setGatewayOrderId("order_" + UUID.randomUUID());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setAmount(invoice.getAmount());
        payment.setMethod(method);
        Payment saved = payments.save(payment);
        return new Order(saved.getId(), gateway, saved.getGatewayOrderId(), invoice.getAmount(), invoice.getCurrency());
    }
}
