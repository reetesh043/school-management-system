package com.school.admission.guards;

import com.school.admission.domain.*;
import com.school.admission.engine.GuardResult;
import com.school.admission.engine.TransitionContext;
import com.school.admission.engine.TransitionGuard;
import com.school.admission.support.DocTypes;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Every mandatory document for the class needs a VERIFIED file as its latest upload. */
@Component
class DocumentsVerifiedGuard implements TransitionGuard {

    private final RequiredDocumentRepository required;
    private final ApplicationDocumentRepository documents;

    DocumentsVerifiedGuard(RequiredDocumentRepository required, ApplicationDocumentRepository documents) {
        this.required = required;
        this.documents = documents;
    }

    @Override
    public String key() {
        return "DOCS_VERIFIED";
    }

    @Override
    public String description() {
        return "All required documents are verified";
    }

    @Override
    public GuardResult check(TransitionContext ctx) {
        AdmissionApplication app = ctx.application();
        Map<String, ApplicationDocument> latest = DocTypes.latestByType(documents.findByApplicationId(app.getId()));

        List<String> missing = required.findByClassConfigId(app.getClassConfigId()).stream()
                .filter(RequiredDocument::isMandatory)
                .filter(r -> {
                    ApplicationDocument d = latest.get(r.getDocType());
                    return d == null || !"VERIFIED".equals(d.getVerificationStatus());
                })
                .map(r -> DocTypes.label(r.getDocType()))
                .sorted()
                .toList();

        if (missing.isEmpty()) {
            return GuardResult.ok();
        }
        return GuardResult.fail(missing.size() + (missing.size() == 1 ? " document is" : " documents are")
                + " not verified yet (" + String.join(", ", missing) + ").");
    }
}
