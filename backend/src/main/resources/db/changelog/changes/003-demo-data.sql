--liquibase formatted sql

--changeset admissions:020-demo-users context:demo
--comment Sample users. Every demo account uses the password Demo@1234 (stored as a bcrypt hash).
INSERT INTO app_user (username, password_hash, full_name, email, phone) VALUES
    ('admin',       '{bcrypt}$2a$10$9UUKzJp1Yxmn.EojUA36He1hl.QXU5Te5mlQESGtlusXEfbZnbN4.', 'Priya Menon',       'admin@school.test',       '+919810000010'),
    ('frontoffice', '{bcrypt}$2a$10$9UUKzJp1Yxmn.EojUA36He1hl.QXU5Te5mlQESGtlusXEfbZnbN4.', 'Suresh Rao',        'frontoffice@school.test', '+919810000011'),
    ('meera',       '{bcrypt}$2a$10$9UUKzJp1Yxmn.EojUA36He1hl.QXU5Te5mlQESGtlusXEfbZnbN4.', 'Meera Krishnan',    'meera@school.test',       '+919810000012'),
    ('rohit',       '{bcrypt}$2a$10$9UUKzJp1Yxmn.EojUA36He1hl.QXU5Te5mlQESGtlusXEfbZnbN4.', 'Rohit Desai',       'rohit@school.test',       '+919810000013'),
    ('committee',   '{bcrypt}$2a$10$9UUKzJp1Yxmn.EojUA36He1hl.QXU5Te5mlQESGtlusXEfbZnbN4.', 'Dr. Anita Kulkarni','committee@school.test',   '+919810000014'),
    ('accounts',    '{bcrypt}$2a$10$9UUKzJp1Yxmn.EojUA36He1hl.QXU5Te5mlQESGtlusXEfbZnbN4.', 'Farhan Sheikh',     'accounts@school.test',    '+919810000015'),
    ('parent',      '{bcrypt}$2a$10$9UUKzJp1Yxmn.EojUA36He1hl.QXU5Te5mlQESGtlusXEfbZnbN4.', 'Neha Sharma',       'neha.sharma@example.com', '+919810000001');

INSERT INTO user_role (user_id, role)
SELECT u.id, v.role
FROM (VALUES
    ('admin',       'ADMIN'),
    ('frontoffice', 'FRONT_OFFICE'),
    ('meera',       'COUNSELLOR'),
    ('rohit',       'COUNSELLOR'),
    ('committee',   'ADMISSION_COMMITTEE'),
    ('accounts',    'ACCOUNTS'),
    ('parent',      'GUARDIAN')
) AS v(username, role)
JOIN app_user u ON u.username = v.username;

--rollback DELETE FROM user_role;
--rollback DELETE FROM app_user;

--changeset admissions:021-demo-seats-and-slots context:demo
--comment Seats already taken by continuing students. Class 9 is full and Class 6 has one seat left, which makes the seat rules easy to see.
UPDATE class_config SET seats_pre_filled = 41 WHERE class_code = 'NUR';
UPDATE class_config SET seats_pre_filled = 24 WHERE class_code = 'KG';
UPDATE class_config SET seats_pre_filled = 33 WHERE class_code = 'C1';
UPDATE class_config SET seats_pre_filled = 19 WHERE class_code = 'C6';
UPDATE class_config SET seats_pre_filled = 10 WHERE class_code = 'C9';

INSERT INTO interaction_slot (kind, starts_at, ends_at, capacity, booked) VALUES
    ('INTERACTION', DATEADD(DAY, 7, CURRENT_TIMESTAMP), DATEADD(MINUTE, 30, DATEADD(DAY, 7, CURRENT_TIMESTAMP)), 6, 0),
    ('INTERACTION', DATEADD(HOUR, 1, DATEADD(DAY, 7, CURRENT_TIMESTAMP)), DATEADD(MINUTE, 90, DATEADD(DAY, 7, CURRENT_TIMESTAMP)), 6, 0),
    ('INTERACTION', DATEADD(DAY, 8, CURRENT_TIMESTAMP), DATEADD(MINUTE, 30, DATEADD(DAY, 8, CURRENT_TIMESTAMP)), 6, 0),
    ('CAMPUS_TOUR', DATEADD(DAY, 9, CURRENT_TIMESTAMP), DATEADD(HOUR, 1, DATEADD(DAY, 9, CURRENT_TIMESTAMP)), 20, 0);

--rollback DELETE FROM interaction_slot;

--changeset admissions:022-demo-leads context:demo
INSERT INTO lead (guardian_name, phone, email, child_name, child_dob, class_code, source, status, consent,
                  assigned_to_id, next_follow_up_at, created_at)
SELECT v.guardian, v.phone, v.email, v.child, CAST(v.dob AS DATE), v.class_code, v.source, v.status, TRUE,
       u.id, DATEADD(DAY, v.follow_up_in, CURRENT_TIMESTAMP), DATEADD(DAY, -v.created_ago, CURRENT_TIMESTAMP)
FROM (VALUES
    ('Rohan Mehta',     '+919820000101', 'rohan.m@example.com',    'Vihaan Mehta',    '2020-11-03', 'C1',  'WEBSITE',    'CONTACTED',       'meera', -1,  9),
    ('Harpreet Singh',  '+919820000102', 'harpreet.s@example.com', 'Kabir Singh',     '2022-06-10', 'KG',  'REFERRAL',   'CONVERTED',       'meera',  3,  8),
    ('Anil Nair',       '+919820000103', 'anil.n@example.com',     'Diya Nair',       '2020-02-02', 'C1',  'GOOGLE_ADS', 'CONVERTED',       'rohit',  2,  7),
    ('Lakshmi Iyer',    '+919820000104', 'lakshmi.i@example.com',  'Anaya Iyer',      '2023-09-01', 'NUR', 'WALK_IN',    'CONVERTED',       'meera',  1, 14),
    ('Sanjay Gupta',    '+919820000105', 'sanjay.g@example.com',   'Reyansh Gupta',   '2015-05-20', 'C6',  'FACEBOOK',   'CONVERTED',       'rohit',  4, 22),
    ('Imran Khan',      '+919820000106', 'imran.k@example.com',    'Myra Khan',       '2015-01-09', 'C6',  'WEBSITE',    'CONVERTED',       'meera',  5, 26),
    ('Debashish Bose',  '+919820000107', 'deb.b@example.com',      'Tara Bose',       '2022-03-14', 'KG',  'REFERRAL',   'CONVERTED',       'rohit',  9, 32),
    ('Kavita Verma',    '+919820000108', 'kavita.v@example.com',   'Ishaan Verma',    '2012-10-30', 'C9',  'EDU_FAIR',   'CONVERTED',       'meera',  6, 20),
    ('Ritu Kapoor',     '+919820000109', 'ritu.k@example.com',     'Ira Kapoor',      '2021-04-18', 'C1',  'FACEBOOK',   'NEW',             'rohit', -2,  2),
    ('Vikram Joshi',    '+919820000110', 'vikram.j@example.com',   'Advait Joshi',    '2019-12-25', 'C1',  'GOOGLE_ADS', 'INTERESTED',      'meera',  0,  5),
    ('Farah Ansari',    '+919820000111', 'farah.a@example.com',    'Zoya Ansari',     '2023-01-30', 'NUR', 'WALK_IN',    'VISIT_SCHEDULED', 'rohit',  1,  3),
    ('Pooja Reddy',     '+919820000112', 'pooja.r@example.com',    'Saanvi Reddy',    '2021-01-05', 'C1',  'WEBSITE',    'CONTACTED',       'meera', -3, 11)
) AS v(guardian, phone, email, child, dob, class_code, source, status, counsellor, follow_up_in, created_ago)
JOIN app_user u ON u.username = v.counsellor;

--rollback DELETE FROM lead;

--changeset admissions:023-demo-applications context:demo
--comment One application per stage so every screen has something to show. APP27-000001 belongs to the demo parent login.
INSERT INTO application (application_no, academic_year_id, class_config_id, workflow_id, stage_id, lead_id,
                         guardian_user_id, assigned_to_id, child_name, child_dob, gender, guardian_name,
                         guardian_phone, guardian_email, form_definition_id, form_version, responses,
                         submitted_at, admission_no, created_at)
SELECT v.application_no, c.academic_year_id, c.id, c.workflow_id, s.id, l.id,
       g.id, a.id, v.child, CAST(v.dob AS DATE), v.gender, v.guardian, v.phone, v.email, f.id, f.version, '{}',
       CASE WHEN s.seq_no >= 20 THEN DATEADD(DAY, -(v.created_ago - 1), CURRENT_TIMESTAMP) ELSE NULL END,
       v.admission_no, DATEADD(DAY, -v.created_ago, CURRENT_TIMESTAMP)
FROM (VALUES
    ('APP27-000001', 'C1',  'ENQUIRY',      'Aanya Sharma',  '2020-08-14', 'Female', 'Neha Sharma',    '+919810000001', 'neha.sharma@example.com', 'parent', CAST(NULL AS VARCHAR(50)), CAST(NULL AS VARCHAR(20)),  2, CAST(NULL AS VARCHAR(30))),
    ('APP27-000002', 'KG',  'APPLICATION',  'Kabir Singh',   '2022-06-10', 'Male',   'Harpreet Singh', '+919820000102', 'harpreet.s@example.com',  NULL,     'meera',                     '+919820000102',  6, NULL),
    ('APP27-000003', 'C1',  'APPLICATION',  'Diya Nair',     '2020-02-02', 'Female', 'Anil Nair',      '+919820000103', 'anil.n@example.com',      NULL,     'rohit',                     '+919820000103',  5, NULL),
    ('APP27-000004', 'NUR', 'REGISTRATION', 'Anaya Iyer',    '2023-09-01', 'Female', 'Lakshmi Iyer',   '+919820000104', 'lakshmi.i@example.com',   NULL,     'meera',                     '+919820000104', 12, NULL),
    ('APP27-000005', 'C6',  'INITIATED',    'Reyansh Gupta', '2015-05-20', 'Male',   'Sanjay Gupta',   '+919820000105', 'sanjay.g@example.com',    NULL,     'rohit',                     '+919820000105', 20, NULL),
    ('APP27-000006', 'C6',  'PAYMENT',      'Myra Khan',     '2015-01-09', 'Female', 'Imran Khan',     '+919820000106', 'imran.k@example.com',     NULL,     'meera',                     '+919820000106', 24, NULL),
    ('APP27-000007', 'KG',  'ADMITTED',     'Tara Bose',     '2022-03-14', 'Female', 'Debashish Bose', '+919820000107', 'deb.b@example.com',       NULL,     'rohit',                     '+919820000107', 30, 'ADM27-00007'),
    ('APP27-000008', 'C9',  'INITIATED',    'Ishaan Verma',  '2012-10-30', 'Male',   'Kavita Verma',   '+919820000108', 'kavita.v@example.com',    NULL,     'meera',                     '+919820000108', 18, NULL)
) AS v(application_no, class_code, stage_code, child, dob, gender, guardian, phone, email, parent_user, assignee, lead_phone, created_ago, admission_no)
JOIN class_config c ON c.class_code = v.class_code
JOIN stage_definition s ON s.workflow_id = c.workflow_id AND s.code = v.stage_code
LEFT JOIN app_user g ON g.username = v.parent_user
LEFT JOIN app_user a ON a.username = v.assignee
LEFT JOIN lead l ON l.phone = v.lead_phone
CROSS JOIN form_definition f
WHERE f.name = 'Admission application form' AND f.version = 1;

-- APP27-000001 is the parent's own unfinished draft, so it starts with an empty form and no lead
UPDATE application SET lead_id = NULL, guardian_phone = '+919810000001' WHERE application_no = 'APP27-000001';

-- Stage history: one row per stage passed, ending at the current stage
INSERT INTO stage_history (application_id, from_stage_id, to_stage_id, actor_type, actor_id, reason, changed_at)
SELECT a.id,
       (SELECT p.id FROM stage_definition p WHERE p.workflow_id = s.workflow_id AND p.seq_no = s.seq_no - 10),
       s.id, 'SYSTEM', 'system', 'Demo data',
       DATEADD(DAY, (s.seq_no / 10) * 2, a.created_at)
FROM application a
JOIN stage_definition cur ON cur.id = a.stage_id
JOIN stage_definition s ON s.workflow_id = a.workflow_id
                       AND s.seq_no <= cur.seq_no
                       AND s.category IN ('OPEN', 'TERMINAL_SUCCESS');

--rollback DELETE FROM stage_history;
--rollback DELETE FROM application;

--changeset admissions:024-demo-documents-assessments-payments context:demo
-- Documents: APP27-000002 has one verified and one waiting, APP27-000003 has everything waiting for verification,
-- and every application from Registration onwards is fully verified.
INSERT INTO application_document (application_id, doc_type, file_name, content_type, size_bytes, storage_key,
                                  verification_status, verified_by_id, verified_at, uploaded_at)
SELECT a.id, rd.doc_type, LOWER(rd.doc_type) || '.pdf', 'application/pdf', 48213,
       'demo/' || a.application_no || '/' || rd.doc_type || '.pdf',
       CASE WHEN a.application_no = 'APP27-000002' AND rd.doc_type = 'BIRTH_CERT' THEN 'VERIFIED'
            WHEN a.application_no IN ('APP27-000002', 'APP27-000003') THEN 'UPLOADED'
            ELSE 'VERIFIED' END,
       CASE WHEN a.application_no IN ('APP27-000002', 'APP27-000003') AND rd.doc_type <> 'BIRTH_CERT' THEN NULL
            WHEN a.application_no = 'APP27-000003' THEN NULL
            ELSE (SELECT u.id FROM app_user u WHERE u.username = 'frontoffice') END,
       CASE WHEN a.application_no IN ('APP27-000002', 'APP27-000003') AND rd.doc_type <> 'BIRTH_CERT' THEN NULL
            WHEN a.application_no = 'APP27-000003' THEN NULL
            ELSE DATEADD(DAY, 1, a.submitted_at) END,
       a.submitted_at
FROM application a
JOIN stage_definition cur ON cur.id = a.stage_id
JOIN required_document rd ON rd.class_config_id = a.class_config_id AND rd.mandatory = TRUE
WHERE cur.seq_no >= 20;

-- Interaction scores for everyone who reached Admission initiated or beyond
INSERT INTO assessment (application_id, kind, score, passed, remarks, evaluated_by_id, evaluated_at)
SELECT a.id, 'INTERACTION', v.score, TRUE, 'Confident and curious. Recommended.',
       (SELECT u.id FROM app_user u WHERE u.username = 'committee'), DATEADD(DAY, 3, a.submitted_at)
FROM (VALUES ('APP27-000005', 72), ('APP27-000006', 68), ('APP27-000007', 81), ('APP27-000008', 65)) AS v(application_no, score)
JOIN application a ON a.application_no = v.application_no;

-- Seats: the paying application holds one, the admitted application has one confirmed
INSERT INTO seat_allocation (class_config_id, application_id, status, held_until)
SELECT a.class_config_id, a.id,
       CASE WHEN a.admission_no IS NULL THEN 'HELD' ELSE 'CONFIRMED' END,
       CASE WHEN a.admission_no IS NULL THEN DATEADD(DAY, 3, CURRENT_TIMESTAMP) ELSE NULL END
FROM application a
WHERE a.application_no IN ('APP27-000006', 'APP27-000007');

INSERT INTO invoice (invoice_no, application_id, description, amount, currency, status, issued_at, due_at, paid_at)
SELECT 'INV-' || a.application_no, a.id, 'Admission fee ' || c.display_name, c.admission_fee, 'INR',
       CASE WHEN a.admission_no IS NULL THEN 'ISSUED' ELSE 'PAID' END,
       DATEADD(DAY, -2, CURRENT_TIMESTAMP), DATEADD(DAY, 3, CURRENT_TIMESTAMP),
       CASE WHEN a.admission_no IS NULL THEN NULL ELSE DATEADD(DAY, -1, CURRENT_TIMESTAMP) END
FROM application a
JOIN class_config c ON c.id = a.class_config_id
WHERE a.application_no IN ('APP27-000006', 'APP27-000007');

INSERT INTO payment (invoice_id, gateway, gateway_order_id, gateway_payment_id, idempotency_key, amount, status, method, settled_at)
SELECT i.id, 'mockpay', 'order_demo_7', 'pay_demo_7', 'demo-idem-7', i.amount, 'SUCCESS', 'UPI', i.paid_at
FROM invoice i
WHERE i.invoice_no = 'INV-APP27-000007';

--rollback DELETE FROM payment;
--rollback DELETE FROM invoice;
--rollback DELETE FROM seat_allocation;
--rollback DELETE FROM assessment;
--rollback DELETE FROM application_document;
