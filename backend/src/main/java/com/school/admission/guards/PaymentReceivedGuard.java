package com.school.admission.guards;

import com.school.admission.domain.InvoiceRepository;
import com.school.admission.engine.GuardResult;
import com.school.admission.engine.TransitionContext;
import com.school.admission.engine.TransitionGuard;
import org.springframework.stereotype.Component;

/** The invoice must be PAID. Only the verified payment webhook sets that, never the browser. */
@Component
class PaymentReceivedGuard implements TransitionGuard {

    private final InvoiceRepository invoices;

    PaymentReceivedGuard(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Override
    public String key() {
        return "PAYMENT_RECEIVED";
    }

    @Override
    public String description() {
        return "Admission fee has been received";
    }

    @Override
    public GuardResult check(TransitionContext ctx) {
        return invoices.existsByApplicationIdAndStatus(ctx.application().getId(), "PAID")
                ? GuardResult.ok()
                : GuardResult.fail("The admission fee has not been received yet.");
    }
}
