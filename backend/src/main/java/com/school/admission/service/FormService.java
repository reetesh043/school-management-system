package com.school.admission.service;

import com.school.admission.domain.FormDefinition;
import com.school.admission.domain.FormDefinitionRepository;
import com.school.admission.domain.FormField;
import com.school.admission.domain.FormFieldRepository;
import com.school.admission.support.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Forms are versioned: publishing never edits a version already in use, it creates the next one. */
@Service
public class FormService {

    private final FormDefinitionRepository forms;
    private final FormFieldRepository fields;

    public FormService(FormDefinitionRepository forms, FormFieldRepository fields) {
        this.forms = forms;
        this.fields = fields;
    }

    @Transactional(readOnly = true)
    public FormDefinition currentApplicationForm() {
        return forms.findFirstByPurposeAndStatusOrderByVersionDesc("APPLICATION", "PUBLISHED")
                .orElseThrow(() -> new BusinessRuleException("No application form has been published yet."));
    }

    @Transactional(readOnly = true)
    public List<FormField> fieldsOf(Long formDefinitionId) {
        return fields.findByFormDefinitionIdOrderBySeqNo(formDefinitionId);
    }

    @Transactional
    public FormDefinition draftFrom(String name) {
        List<FormDefinition> versions = forms.findByNameOrderByVersionDesc(name);
        if (versions.isEmpty()) {
            throw new BusinessRuleException("No form named " + name + " exists yet.");
        }
        FormDefinition latest = versions.get(0);
        FormDefinition draft = new FormDefinition();
        draft.setName(latest.getName());
        draft.setPurpose(latest.getPurpose());
        draft.setVersion(latest.getVersion() + 1);
        draft.setStatus("DRAFT");
        FormDefinition saved = forms.save(draft);

        for (FormField f : fields.findByFormDefinitionIdOrderBySeqNo(latest.getId())) {
            FormField copy = new FormField();
            copy.setFormDefinitionId(saved.getId());
            copy.setFieldKey(f.getFieldKey());
            copy.setLabel(f.getLabel());
            copy.setFieldType(f.getFieldType());
            copy.setMandatory(f.isMandatory());
            copy.setSeqNo(f.getSeqNo());
            copy.setOptions(f.getOptions());
            fields.save(copy);
        }
        return saved;
    }

    @Transactional
    public FormDefinition publish(Long formDefinitionId) {
        FormDefinition form = forms.findById(formDefinitionId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Form not found"));
        if (!"DRAFT".equals(form.getStatus())) {
            throw new BusinessRuleException("Only a draft form can be published.");
        }
        form.setStatus("PUBLISHED");
        form.setPublishedAt(Instant.now());
        return form;
    }
}
