package com.school.admission.service;

import com.school.admission.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Renders a message_template and writes a communication_log row. Sending is simulated (logged), not dispatched. */
@Service
public class CommunicationService {

    private final MessageTemplateRepository templates;
    private final CommunicationLogRepository logs;
    private final AdmissionApplicationRepository applications;
    private final ClassConfigRepository classes;
    private final InvoiceRepository invoices;

    public CommunicationService(MessageTemplateRepository templates, CommunicationLogRepository logs,
                                AdmissionApplicationRepository applications, ClassConfigRepository classes,
                                InvoiceRepository invoices) {
        this.templates = templates;
        this.logs = logs;
        this.applications = applications;
        this.classes = classes;
        this.invoices = invoices;
    }

    @Transactional
    public void sendStageMessage(Long applicationId, String templateCode) {
        AdmissionApplication app = applications.findById(applicationId).orElseThrow();
        ClassConfig cls = classes.findById(app.getClassConfigId()).orElse(null);
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("childName", app.getChildName());
        vars.put("guardianName", app.getGuardianName());
        vars.put("applicationNo", app.getApplicationNo());
        vars.put("className", cls != null ? cls.getDisplayName() : "");
        vars.put("admissionNo", app.getAdmissionNo());
        invoices.findFirstByApplicationIdAndStatus(applicationId, "ISSUED").ifPresentOrElse(
                inv -> {
                    vars.put("amount", inv.getAmount().stripTrailingZeros().toPlainString());
                    vars.put("dueDate", inv.getDueAt().toString().substring(0, 10));
                },
                () -> { vars.put("amount", ""); vars.put("dueDate", ""); });

        List<MessageTemplate> variants = templates.findByCode(templateCode);
        if (variants.isEmpty()) {
            log(applicationId, "SYSTEM", templateCode, null, "No template configured for " + templateCode, "FAILED");
            return;
        }
        for (MessageTemplate t : variants) {
            String rendered = render(t.getBody(), vars);
            String to = "EMAIL".equals(t.getChannel()) ? app.getGuardianEmail() : app.getGuardianPhone();
            log(applicationId, t.getChannel(), templateCode, to, rendered, "SENT");
        }
    }

    private void log(Long applicationId, String channel, String templateCode, String to, String message, String status) {
        CommunicationLog l = new CommunicationLog();
        l.setApplicationId(applicationId);
        l.setChannel(channel);
        l.setTemplateCode(templateCode);
        l.setToAddress(to);
        l.setMessage(message);
        l.setStatus(status);
        logs.save(l);
    }

    private static String render(String template, Map<String, String> vars) {
        String out = template;
        for (var e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<CommunicationLog> forApplication(Long applicationId) {
        return logs.findByApplicationIdOrderByIdDesc(applicationId);
    }
}
