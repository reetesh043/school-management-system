--liquibase formatted sql

--changeset admissions:010-academic-year-and-workflow
--comment Loaded in every environment. Guard and action keys must match the Spring beans in the guards and actions packages.

INSERT INTO academic_year (label, starts_on, ends_on, age_cutoff_date, admissions_open)
VALUES ('2027-28', DATE '2027-04-01', DATE '2028-03-31', DATE '2027-03-31', TRUE);

INSERT INTO workflow (name, version, status) VALUES ('Default admission workflow', 1, 'ACTIVE');

INSERT INTO stage_definition (workflow_id, code, name, seq_no, category)
SELECT w.id, v.code, v.name, v.seq_no, v.category
FROM (VALUES
    ('ENQUIRY',      'Enquiry',             10, 'OPEN'),
    ('APPLICATION',  'Application',         20, 'OPEN'),
    ('REGISTRATION', 'Registration',        30, 'OPEN'),
    ('INITIATED',    'Admission initiated', 40, 'OPEN'),
    ('PAYMENT',      'Payment',             50, 'OPEN'),
    ('ADMITTED',     'Admitted',            60, 'TERMINAL_SUCCESS'),
    ('WAITLISTED',   'Waitlisted',          90, 'HOLD'),
    ('REJECTED',     'Rejected',            99, 'TERMINAL_FAIL')
) AS v(code, name, seq_no, category)
CROSS JOIN workflow w
WHERE w.name = 'Default admission workflow' AND w.version = 1;

INSERT INTO stage_transition (workflow_id, from_stage_id, to_stage_id, allowed_roles, guard_keys, action_keys)
SELECT w.id, f.id, t.id, v.roles, v.guards, v.actions
FROM (VALUES
    -- forward path
    ('ENQUIRY',      'APPLICATION',  'GUARDIAN,COUNSELLOR,FRONT_OFFICE,ADMIN', 'AGE_ELIGIBLE,FORM_COMPLETE', 'NOTIFY'),
    ('APPLICATION',  'REGISTRATION', 'COUNSELLOR,FRONT_OFFICE,ADMIN',          'DOCS_VERIFIED',              'NOTIFY'),
    ('REGISTRATION', 'INITIATED',    'ADMISSION_COMMITTEE,ADMIN',              'ASSESSMENT_PASSED',          'NOTIFY'),
    ('INITIATED',    'PAYMENT',      'ADMISSION_COMMITTEE,ADMIN',              'SEAT_AVAILABLE',             'HOLD_SEAT,ISSUE_INVOICE,NOTIFY'),
    ('PAYMENT',      'ADMITTED',     'SYSTEM,ACCOUNTS,ADMIN',                  'PAYMENT_RECEIVED',           'CONFIRM_ADMISSION,NOTIFY'),
    -- waitlist and reject from every open stage (a held seat is released when leaving PAYMENT)
    ('ENQUIRY',      'WAITLISTED',   'ADMISSION_COMMITTEE,ADMIN', '', 'NOTIFY'),
    ('APPLICATION',  'WAITLISTED',   'ADMISSION_COMMITTEE,ADMIN', '', 'NOTIFY'),
    ('REGISTRATION', 'WAITLISTED',   'ADMISSION_COMMITTEE,ADMIN', '', 'NOTIFY'),
    ('INITIATED',    'WAITLISTED',   'ADMISSION_COMMITTEE,ADMIN', '', 'NOTIFY'),
    ('PAYMENT',      'WAITLISTED',   'ADMISSION_COMMITTEE,ADMIN', '', 'RELEASE_SEAT,NOTIFY'),
    ('ENQUIRY',      'REJECTED',     'ADMISSION_COMMITTEE,ADMIN', '', 'NOTIFY'),
    ('APPLICATION',  'REJECTED',     'ADMISSION_COMMITTEE,ADMIN', '', 'NOTIFY'),
    ('REGISTRATION', 'REJECTED',     'ADMISSION_COMMITTEE,ADMIN', '', 'NOTIFY'),
    ('INITIATED',    'REJECTED',     'ADMISSION_COMMITTEE,ADMIN', '', 'NOTIFY'),
    ('PAYMENT',      'REJECTED',     'ADMISSION_COMMITTEE,ADMIN', '', 'RELEASE_SEAT,NOTIFY'),
    -- a waitlisted application can be brought back or closed
    ('WAITLISTED',   'INITIATED',    'ADMISSION_COMMITTEE,ADMIN', '', 'NOTIFY'),
    ('WAITLISTED',   'REJECTED',     'ADMISSION_COMMITTEE,ADMIN', '', 'NOTIFY')
) AS v(from_code, to_code, roles, guards, actions)
CROSS JOIN workflow w
JOIN stage_definition f ON f.workflow_id = w.id AND f.code = v.from_code
JOIN stage_definition t ON t.workflow_id = w.id AND t.code = v.to_code
WHERE w.name = 'Default admission workflow' AND w.version = 1;

--rollback DELETE FROM stage_transition;
--rollback DELETE FROM stage_definition;
--rollback DELETE FROM workflow;
--rollback DELETE FROM academic_year;

--changeset admissions:011-classes-fees-documents
INSERT INTO class_config (academic_year_id, class_code, display_name, min_age_years, max_age_years,
                          seats_total, seats_pre_filled, pass_mark, admission_fee, fee_due_days, workflow_id)
SELECT ay.id, v.class_code, v.display_name, v.min_age, v.max_age, v.seats, 0, v.pass_mark, v.fee, 5, w.id
FROM (VALUES
    ('NUR', 'Nursery',  3,  4, 60, 50, 25000),
    ('LKG', 'LKG',      4,  5, 60, 50, 26000),
    ('UKG', 'UKG',      5,  6, 60, 50, 27000),
    ('C1',  'Class 1',  6,  7, 45, 50, 30000),
    ('C2',  'Class 2',  7,  8, 45, 50, 31000),
    ('C3',  'Class 3',  8,  9, 45, 50, 32000),
    ('C4',  'Class 4',  9, 10, 45, 50, 33000),
    ('C5',  'Class 5', 10, 11, 45, 50, 34000),
    ('C6',  'Class 6', 11, 12, 40, 50, 35000),
    ('C7',  'Class 7', 12, 13, 40, 50, 36000),
    ('C8',  'Class 8', 13, 14, 40, 50, 37000),
    ('C9',  'Class 9', 14, 15, 35, 50, 40000),
    ('C10', 'Class 10',15, 16, 35, 50, 42000),
    ('C11', 'Class 11',16, 17, 35, 50, 45000),
    ('C12', 'Class 12',17, 18, 35, 50, 47000)
) AS v(class_code, display_name, min_age, max_age, seats, pass_mark, fee)
CROSS JOIN academic_year ay
CROSS JOIN workflow w
WHERE ay.label = '2027-28' AND w.name = 'Default admission workflow' AND w.version = 1;

-- Required documents by schooling stage.
-- Birth certificate and address proof apply throughout; report card and transfer certificate apply to transfers into higher classes.
INSERT INTO required_document (class_config_id, doc_type, mandatory)
SELECT c.id, d.doc_type, TRUE
FROM class_config c
CROSS JOIN (VALUES ('BIRTH_CERT'), ('ADDRESS_PROOF')) AS d(doc_type);

INSERT INTO required_document (class_config_id, doc_type, mandatory)
SELECT c.id, 'REPORT_CARD', TRUE
FROM class_config c
WHERE c.class_code NOT IN ('NUR','LKG','UKG');

INSERT INTO required_document (class_config_id, doc_type, mandatory)
SELECT c.id, 'TRANSFER_CERT', TRUE
FROM class_config c
WHERE c.class_code IN ('C2','C3','C4','C5','C6','C7','C8','C9','C10','C11','C12');

--rollback DELETE FROM required_document;
--rollback DELETE FROM class_config;

--changeset admissions:012-default-application-form
INSERT INTO form_definition (name, purpose, version, status, published_at)
VALUES ('Admission application form', 'APPLICATION', 1, 'PUBLISHED', CURRENT_TIMESTAMP);

INSERT INTO form_field (form_definition_id, field_key, label, field_type, mandatory, seq_no, options)
SELECT f.id, v.field_key, v.label, v.field_type, v.mandatory, v.seq_no, v.options
FROM (VALUES
    ('residential_address', 'Residential address',                 'TEXTAREA', TRUE,  10, CAST(NULL AS VARCHAR(1000))),
    ('preferred_language',  'Language spoken at home',             'SELECT',   FALSE, 20, 'English,Hindi,Marathi,Tamil,Bengali,Other'),
    ('transport_required',  'Will your child need school transport?', 'SELECT', TRUE,  30, 'Yes,No'),
    ('sibling_in_school',   'A brother or sister already studies here', 'CHECKBOX', FALSE, 40, CAST(NULL AS VARCHAR(1000))),
    ('sibling_name',        'Sibling name and class',              'TEXT',     FALSE, 50, CAST(NULL AS VARCHAR(1000)))
) AS v(field_key, label, field_type, mandatory, seq_no, options)
CROSS JOIN form_definition f
WHERE f.name = 'Admission application form' AND f.version = 1;

--rollback DELETE FROM form_field;
--rollback DELETE FROM form_definition;

--changeset admissions:013-message-templates
--comment Placeholders: {{childName}} {{guardianName}} {{applicationNo}} {{className}} {{amount}} {{dueDate}} {{admissionNo}}
INSERT INTO message_template (code, channel, body) VALUES
    ('STAGE_ENQUIRY',      'WHATSAPP', 'Hello {{guardianName}}, thanks for your interest in admission for {{childName}} ({{className}}). Your application {{applicationNo}} has been started. Complete the form to continue.'),
    ('STAGE_APPLICATION',  'WHATSAPP', 'Hello {{guardianName}}, we received the application for {{childName}} ({{applicationNo}}). Please upload the required documents so we can verify them.'),
    ('STAGE_REGISTRATION', 'WHATSAPP', 'Good news, {{guardianName}}: the documents for {{childName}} are verified. Please book an interaction slot to complete registration.'),
    ('STAGE_INITIATED',    'WHATSAPP', 'Hello {{guardianName}}, {{childName}} has cleared the interaction. We are confirming a seat in {{className}} and will update you shortly.'),
    ('STAGE_PAYMENT',      'WHATSAPP', 'A seat in {{className}} is held for {{childName}}. Please pay the admission fee of Rs. {{amount}} by {{dueDate}} to confirm it.'),
    ('STAGE_PAYMENT',      'EMAIL',    'Dear {{guardianName}}, a seat in {{className}} is held for {{childName}}. Please pay the admission fee of Rs. {{amount}} by {{dueDate}} to confirm the admission.'),
    ('STAGE_ADMITTED',     'WHATSAPP', 'Welcome, {{guardianName}}! {{childName}} is admitted to {{className}}. Admission number: {{admissionNo}}.'),
    ('STAGE_ADMITTED',     'EMAIL',    'Dear {{guardianName}}, we are delighted to confirm the admission of {{childName}} to {{className}}. Admission number: {{admissionNo}}. Our office will share joining details shortly.'),
    ('STAGE_WAITLISTED',   'WHATSAPP', 'Hello {{guardianName}}, the application for {{childName}} ({{className}}) is on our waitlist. We will contact you as soon as a seat opens.'),
    ('STAGE_REJECTED',     'WHATSAPP', 'Hello {{guardianName}}, we are sorry that we cannot offer {{childName}} a seat in {{className}} this year. Thank you for applying.');

--rollback DELETE FROM message_template;
