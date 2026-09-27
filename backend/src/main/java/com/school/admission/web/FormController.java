package com.school.admission.web;

import com.school.admission.domain.FormDefinition;
import com.school.admission.domain.FormField;
import com.school.admission.service.FormService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/forms")
@Tag(name = "Forms and config", description = "The versioned application form")
class FormController {

    public record FieldRow(String fieldKey, String label, String fieldType, boolean mandatory, int seqNo, List<String> options) { }
    public record FormRow(Long id, String name, int version, String status, List<FieldRow> fields) { }

    private final FormService forms;

    FormController(FormService forms) {
        this.forms = forms;
    }

    @GetMapping("/current")
    FormRow current() {
        FormDefinition form = forms.currentApplicationForm();
        List<FieldRow> fields = forms.fieldsOf(form.getId()).stream()
                .map(f -> new FieldRow(f.getFieldKey(), f.getLabel(), f.getFieldType(), f.isMandatory(), f.getSeqNo(),
                        f.getOptions() == null ? List.of() : List.of(f.getOptions().split(","))))
                .toList();
        return new FormRow(form.getId(), form.getName(), form.getVersion(), form.getStatus(), fields);
    }

    @PostMapping("/{name}/new-draft")
    @PreAuthorize("hasRole('ADMIN')")
    FormRow draft(@PathVariable String name) {
        FormDefinition draft = forms.draftFrom(name);
        return new FormRow(draft.getId(), draft.getName(), draft.getVersion(), draft.getStatus(), List.of());
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    FormRow publish(@PathVariable Long id) {
        FormDefinition form = forms.publish(id);
        return new FormRow(form.getId(), form.getName(), form.getVersion(), form.getStatus(), List.of());
    }
}
