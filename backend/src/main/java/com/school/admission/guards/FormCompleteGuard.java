package com.school.admission.guards;

import com.school.admission.domain.AdmissionApplication;
import com.school.admission.domain.FormField;
import com.school.admission.domain.FormFieldRepository;
import com.school.admission.engine.GuardResult;
import com.school.admission.engine.TransitionContext;
import com.school.admission.engine.TransitionGuard;
import com.school.admission.support.Json;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Core details plus every mandatory field of the form version the application was started with. */
@Component
class FormCompleteGuard implements TransitionGuard {

    private final FormFieldRepository fields;
    private final Json json;

    FormCompleteGuard(FormFieldRepository fields, Json json) {
        this.fields = fields;
        this.json = json;
    }

    @Override
    public String key() {
        return "FORM_COMPLETE";
    }

    @Override
    public String description() {
        return "Child, parent and all mandatory form fields are filled in";
    }

    @Override
    public GuardResult check(TransitionContext ctx) {
        AdmissionApplication app = ctx.application();
        List<String> missing = new ArrayList<>();
        if (blank(app.getChildName())) missing.add("child's name");
        if (blank(app.getGuardianName())) missing.add("parent or guardian name");
        if (blank(app.getGuardianPhone())) missing.add("parent or guardian phone");

        if (app.getFormDefinitionId() != null) {
            Map<String, Object> answers = json.toMap(app.getResponses());
            for (FormField f : fields.findByFormDefinitionIdOrderBySeqNo(app.getFormDefinitionId())) {
                if (f.isMandatory() && blank(answers.get(f.getFieldKey()))) {
                    missing.add(f.getLabel().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (missing.isEmpty()) {
            return GuardResult.ok();
        }
        return GuardResult.fail("Please fill in: " + String.join(", ", missing) + ".");
    }

    private static boolean blank(Object value) {
        return value == null || value.toString().isBlank();
    }
}
