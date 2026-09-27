package com.school.admission.actions;

import com.school.admission.domain.ClassConfig;
import com.school.admission.domain.ClassConfigRepository;
import com.school.admission.domain.Invoice;
import com.school.admission.domain.InvoiceRepository;
import com.school.admission.engine.TransitionAction;
import com.school.admission.engine.TransitionContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Creates the admission-fee invoice when payment opens. Re-entering PAYMENT reuses the open invoice. */
@Component
class IssueInvoiceAction implements TransitionAction {

    private final InvoiceRepository invoices;
    private final ClassConfigRepository classes;

    IssueInvoiceAction(InvoiceRepository invoices, ClassConfigRepository classes) {
        this.invoices = invoices;
        this.classes = classes;
    }

    @Override
    public String key() {
        return "ISSUE_INVOICE";
    }

    @Override
    public void execute(TransitionContext ctx) {
        Long appId = ctx.application().getId();
        if (invoices.findFirstByApplicationIdAndStatus(appId, "ISSUED").isPresent()) {
            return;
        }
        ClassConfig cls = classes.findById(ctx.application().getClassConfigId()).orElseThrow();
        Invoice invoice = new Invoice();
        invoice.setInvoiceNo("INV-" + ctx.application().getApplicationNo() + "-" + (invoices.findByApplicationIdOrderByIdDesc(appId).size() + 1));
        invoice.setApplicationId(appId);
        invoice.setDescription("Admission fee, " + cls.getDisplayName());
        invoice.setAmount(cls.getAdmissionFee());
        invoice.setDueAt(Instant.now().plus(cls.getFeeDueDays(), ChronoUnit.DAYS));
        invoices.save(invoice);
    }
}
