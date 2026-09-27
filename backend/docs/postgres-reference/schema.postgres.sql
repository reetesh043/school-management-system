-- =====================================================================
-- School Admission Management System : PostgreSQL 15+ schema
-- Conventions
--   * uuid primary keys (gen_random_uuid), timestamptz everywhere
--   * enums are text + CHECK so adding a value is a cheap migration
--   * tenant hierarchy: school_group > school > campus
--   * PII lives in applicant / guardian / app_user / lead (encrypt at rest
--     with KMS-managed storage encryption; mask in logs)
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------
-- 1. Tenancy
-- ---------------------------------------------------------------------
CREATE TABLE school_group (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE school (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id    uuid REFERENCES school_group(id),
    name        text NOT NULL,
    board       text NOT NULL CHECK (board IN ('CBSE','CISCE','IB','IGCSE','STATE','OTHER')),
    timezone    text NOT NULL DEFAULT 'Asia/Kolkata',
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE campus (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid NOT NULL REFERENCES school(id),
    name        text NOT NULL,
    city        text,
    UNIQUE (school_id, name)
);

CREATE TABLE academic_year (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        uuid NOT NULL REFERENCES school(id),
    label            text NOT NULL,                 -- e.g. '2027-28'
    starts_on        date NOT NULL,
    ends_on          date NOT NULL,
    age_cutoff_date  date NOT NULL,                 -- age is evaluated as on this date
    admissions_open  boolean NOT NULL DEFAULT false,
    UNIQUE (school_id, label)
);

-- ---------------------------------------------------------------------
-- 2. Users and roles (RBAC)
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid NOT NULL REFERENCES school(id),
    user_type   text NOT NULL CHECK (user_type IN ('STAFF','GUARDIAN')),
    full_name   text NOT NULL,
    email       text,
    phone_e164  text,
    status      text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_user_email ON app_user (school_id, lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX ux_user_phone ON app_user (school_id, phone_e164) WHERE phone_e164 IS NOT NULL;

CREATE TABLE role (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code         text NOT NULL UNIQUE,   -- GUARDIAN, FRONT_OFFICE, COUNSELLOR, ADMISSION_COMMITTEE, ACCOUNTS, ADMIN, GROUP_VIEWER
    name         text NOT NULL,
    permissions  text[] NOT NULL DEFAULT '{}'
);

CREATE TABLE user_role (
    user_id    uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id    uuid NOT NULL REFERENCES role(id),
    campus_id  uuid REFERENCES campus(id),   -- NULL = all campuses
    PRIMARY KEY (user_id, role_id)
);

-- ---------------------------------------------------------------------
-- 3. Configurable forms (versioned)
-- ---------------------------------------------------------------------
CREATE TABLE form_definition (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     uuid NOT NULL REFERENCES school(id),
    name          text NOT NULL,
    purpose       text NOT NULL CHECK (purpose IN ('ENQUIRY','APPLICATION')),
    version       int  NOT NULL DEFAULT 1,
    status        text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    published_at  timestamptz,
    UNIQUE (school_id, name, version)
);

CREATE TABLE form_field (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    form_definition_id  uuid NOT NULL REFERENCES form_definition(id) ON DELETE CASCADE,
    field_key           text NOT NULL,
    label               text NOT NULL,
    section             text,
    field_type          text NOT NULL CHECK (field_type IN ('TEXT','NUMBER','DATE','SELECT','MULTISELECT','PHONE','EMAIL','FILE','CHECKBOX','TEXTAREA')),
    is_mandatory        boolean NOT NULL DEFAULT false,
    sequence            int NOT NULL,
    options             jsonb,     -- for SELECT / MULTISELECT
    validation          jsonb,     -- {"minLength":2,"pattern":"..."}
    UNIQUE (form_definition_id, field_key),
    UNIQUE (form_definition_id, sequence)
);

-- ---------------------------------------------------------------------
-- 4. Workflow (stages, transitions, guards, actions)
-- ---------------------------------------------------------------------
CREATE TABLE workflow (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id  uuid NOT NULL REFERENCES school(id),
    name       text NOT NULL,
    version    int  NOT NULL DEFAULT 1,
    status     text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    UNIQUE (school_id, name, version)
);

CREATE TABLE stage_definition (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id  uuid NOT NULL REFERENCES workflow(id) ON DELETE CASCADE,
    code         text NOT NULL,
    name         text NOT NULL,
    sequence     int  NOT NULL,
    category     text NOT NULL CHECK (category IN ('OPEN','HOLD','TERMINAL_SUCCESS','TERMINAL_FAIL')),
    UNIQUE (workflow_id, code)
);

CREATE TABLE stage_transition (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id    uuid NOT NULL REFERENCES workflow(id) ON DELETE CASCADE,
    from_stage_id  uuid NOT NULL REFERENCES stage_definition(id),
    to_stage_id    uuid NOT NULL REFERENCES stage_definition(id),
    allowed_roles  text[] NOT NULL,                -- roles that may trigger it (SYSTEM for webhooks/jobs)
    guard_keys     text[] NOT NULL DEFAULT '{}',   -- e.g. {AGE_ELIGIBLE,DOCS_VERIFIED}
    action_keys    text[] NOT NULL DEFAULT '{}',   -- e.g. {HOLD_SEAT,ISSUE_INVOICE,NOTIFY}
    UNIQUE (from_stage_id, to_stage_id)
);

-- ---------------------------------------------------------------------
-- 5. Classes, seats, rubric, fees
-- ---------------------------------------------------------------------
CREATE TABLE class_config (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    campus_id             uuid NOT NULL REFERENCES campus(id),
    academic_year_id      uuid NOT NULL REFERENCES academic_year(id),
    class_code            text NOT NULL,           -- 'NUR','KG','C1'...
    display_name          text NOT NULL,
    min_age_years         int,
    max_age_years         int,
    seats_total           int NOT NULL CHECK (seats_total >= 0),
    application_form_id   uuid REFERENCES form_definition(id),
    workflow_id           uuid NOT NULL REFERENCES workflow(id),
    pass_mark             numeric(5,2) NOT NULL DEFAULT 50,
    UNIQUE (campus_id, academic_year_id, class_code)
);

CREATE TABLE required_document (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_config_id  uuid NOT NULL REFERENCES class_config(id) ON DELETE CASCADE,
    doc_type         text NOT NULL,                -- BIRTH_CERT, ADDRESS_PROOF, REPORT_CARD, TC
    is_mandatory     boolean NOT NULL DEFAULT true,
    UNIQUE (class_config_id, doc_type)
);

CREATE TABLE evaluation_rubric (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_config_id  uuid NOT NULL REFERENCES class_config(id) ON DELETE CASCADE,
    criterion        text NOT NULL,
    weight           numeric(5,2) NOT NULL CHECK (weight > 0),
    max_score        numeric(5,2) NOT NULL DEFAULT 100
);

CREATE TABLE fee_plan (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_config_id  uuid NOT NULL REFERENCES class_config(id) ON DELETE CASCADE,
    fee_type         text NOT NULL CHECK (fee_type IN ('REGISTRATION','ADMISSION','SECURITY_DEPOSIT','OTHER')),
    name             text NOT NULL,
    amount           numeric(12,2) NOT NULL CHECK (amount >= 0),
    currency         char(3) NOT NULL DEFAULT 'INR',
    due_in_days      int NOT NULL DEFAULT 5
);

-- ---------------------------------------------------------------------
-- 6. Leads
-- ---------------------------------------------------------------------
CREATE TABLE lead (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                 uuid NOT NULL REFERENCES school(id),
    campus_id                 uuid REFERENCES campus(id),
    academic_year_id          uuid NOT NULL REFERENCES academic_year(id),
    source                    text NOT NULL,        -- WEBSITE, WALK_IN, FACEBOOK, GOOGLE_ADS, REFERRAL, FAIR, PHONE
    utm                       jsonb,
    guardian_name             text NOT NULL,
    phone_e164                text NOT NULL,
    email                     text,
    child_name                text,
    child_dob                 date,
    class_code                text,
    status                    text NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','CONTACTED','INTERESTED','VISIT_SCHEDULED','CONVERTED','LOST')),
    lost_reason               text,
    assigned_to               uuid REFERENCES app_user(id),
    next_follow_up_at         timestamptz,
    converted_application_id  uuid,                 -- FK added below (circular)
    created_at                timestamptz NOT NULL DEFAULT now()
);
-- duplicate detection: same guardian phone + child DOB in the same year
CREATE UNIQUE INDEX ux_lead_dedupe ON lead (school_id, academic_year_id, phone_e164, child_dob)
    WHERE child_dob IS NOT NULL AND status <> 'LOST';
CREATE INDEX ix_lead_followup ON lead (assigned_to, next_follow_up_at) WHERE status NOT IN ('CONVERTED','LOST');

CREATE TABLE lead_followup (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id     uuid NOT NULL REFERENCES lead(id) ON DELETE CASCADE,
    due_at      timestamptz NOT NULL,
    done_at     timestamptz,
    outcome     text,
    note        text,
    created_by  uuid REFERENCES app_user(id)
);

-- ---------------------------------------------------------------------
-- 7. Applications
-- ---------------------------------------------------------------------
CREATE TABLE application (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_no     text NOT NULL UNIQUE,
    school_id          uuid NOT NULL REFERENCES school(id),
    campus_id          uuid NOT NULL REFERENCES campus(id),
    academic_year_id   uuid NOT NULL REFERENCES academic_year(id),
    class_config_id    uuid NOT NULL REFERENCES class_config(id),
    workflow_id        uuid NOT NULL REFERENCES workflow(id),
    stage_id           uuid NOT NULL REFERENCES stage_definition(id),
    prev_stage_id      uuid REFERENCES stage_definition(id),   -- where to return on reopen
    lead_id            uuid REFERENCES lead(id),
    guardian_user_id   uuid REFERENCES app_user(id),
    assigned_to        uuid REFERENCES app_user(id),
    form_definition_id uuid REFERENCES form_definition(id),
    form_version       int,
    responses          jsonb NOT NULL DEFAULT '{}'::jsonb,     -- answers to custom fields
    submitted_at       timestamptz,
    admission_no       text UNIQUE,
    version            bigint NOT NULL DEFAULT 0,              -- optimistic locking
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_app_stage      ON application (academic_year_id, class_config_id, stage_id);
CREATE INDEX ix_app_assignee   ON application (assigned_to, stage_id);
CREATE INDEX ix_app_responses  ON application USING gin (responses jsonb_path_ops);

CREATE TABLE applicant (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  uuid NOT NULL UNIQUE REFERENCES application(id) ON DELETE CASCADE,
    full_name       text NOT NULL,
    dob             date NOT NULL,
    gender          text,
    previous_school text
);

CREATE TABLE guardian (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  uuid NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    relation        text NOT NULL,
    full_name       text NOT NULL,
    phone_e164      text NOT NULL,
    email           text,
    is_primary      boolean NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX ux_guardian_primary ON guardian (application_id) WHERE is_primary;

-- Gap-free enough for admission numbers; used by ConfirmAdmissionAction
CREATE SEQUENCE admission_no_seq START 1;

CREATE TABLE application_document (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id       uuid NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    doc_type             text NOT NULL,
    s3_key               text NOT NULL,
    file_name            text NOT NULL,
    content_type         text NOT NULL,
    size_bytes           bigint NOT NULL CHECK (size_bytes > 0),
    scan_status          text NOT NULL DEFAULT 'PENDING' CHECK (scan_status IN ('PENDING','CLEAN','INFECTED')),
    verification_status  text NOT NULL DEFAULT 'UPLOADED' CHECK (verification_status IN ('UPLOADED','VERIFIED','REJECTED')),
    reject_reason        text,
    verified_by          uuid REFERENCES app_user(id),
    verified_at          timestamptz,
    uploaded_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_doc_app ON application_document (application_id, doc_type);

-- Seat ledger: one active row per application. Confirming a seat = counting
-- HELD + CONFIRMED rows against class_config.seats_total under a row lock.
CREATE TABLE seat_allocation (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_config_id  uuid NOT NULL REFERENCES class_config(id),
    application_id   uuid NOT NULL REFERENCES application(id),
    status           text NOT NULL CHECK (status IN ('HELD','CONFIRMED','RELEASED')),
    held_until       timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_seat_active ON seat_allocation (application_id) WHERE status IN ('HELD','CONFIRMED');
CREATE INDEX ix_seat_class ON seat_allocation (class_config_id, status);

-- ---------------------------------------------------------------------
-- 8. Interaction, tours, assessment
-- ---------------------------------------------------------------------
CREATE TABLE interaction_slot (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    campus_id         uuid NOT NULL REFERENCES campus(id),
    academic_year_id  uuid NOT NULL REFERENCES academic_year(id),
    kind              text NOT NULL CHECK (kind IN ('INTERACTION','CAMPUS_TOUR','ENTRANCE_TEST')),
    starts_at         timestamptz NOT NULL,
    ends_at           timestamptz NOT NULL,
    capacity          int NOT NULL CHECK (capacity > 0),
    booked            int NOT NULL DEFAULT 0 CHECK (booked <= capacity)
);

CREATE TABLE interaction_booking (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id         uuid NOT NULL REFERENCES interaction_slot(id),
    application_id  uuid NOT NULL REFERENCES application(id),
    status          text NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED','ATTENDED','NO_SHOW','CANCELLED')),
    UNIQUE (slot_id, application_id)
);

CREATE TABLE assessment (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  uuid NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    kind            text NOT NULL CHECK (kind IN ('INTERACTION','ENTRANCE_TEST')),
    total_score     numeric(6,2) NOT NULL,      -- weighted total
    passed          boolean NOT NULL,
    evaluated_by    uuid REFERENCES app_user(id),
    evaluated_at    timestamptz NOT NULL DEFAULT now(),
    remarks         text
);

CREATE TABLE assessment_score (
    assessment_id  uuid NOT NULL REFERENCES assessment(id) ON DELETE CASCADE,
    rubric_id      uuid NOT NULL REFERENCES evaluation_rubric(id),
    score          numeric(5,2) NOT NULL,
    PRIMARY KEY (assessment_id, rubric_id)
);

-- ---------------------------------------------------------------------
-- 9. Billing and payments
-- ---------------------------------------------------------------------
CREATE TABLE invoice (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_no      text NOT NULL UNIQUE,
    application_id  uuid NOT NULL REFERENCES application(id),
    fee_plan_id     uuid NOT NULL REFERENCES fee_plan(id),
    amount          numeric(12,2) NOT NULL,
    currency        char(3) NOT NULL DEFAULT 'INR',
    status          text NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('DRAFT','ISSUED','PARTIAL','PAID','CANCELLED','EXPIRED')),
    issued_at       timestamptz NOT NULL DEFAULT now(),
    due_at          timestamptz NOT NULL,
    paid_at         timestamptz
);
CREATE INDEX ix_invoice_app ON invoice (application_id, status);

CREATE TABLE payment (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id          uuid NOT NULL REFERENCES invoice(id),
    gateway             text NOT NULL,
    gateway_order_id    text NOT NULL,
    gateway_payment_id  text,
    idempotency_key     text NOT NULL UNIQUE,
    amount              numeric(12,2) NOT NULL,
    status              text NOT NULL DEFAULT 'INITIATED' CHECK (status IN ('INITIATED','SUCCESS','FAILED','REFUNDED','PARTIALLY_REFUNDED')),
    method              text,
    settled_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (gateway, gateway_order_id)
);
CREATE UNIQUE INDEX ux_payment_gateway_pid ON payment (gateway, gateway_payment_id) WHERE gateway_payment_id IS NOT NULL;

-- Every webhook is stored once; the unique key makes redelivery a no-op.
CREATE TABLE payment_webhook_event (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    gateway          text NOT NULL,
    event_id         text NOT NULL,
    payload          jsonb NOT NULL,
    signature_valid  boolean NOT NULL,
    received_at      timestamptz NOT NULL DEFAULT now(),
    processed_at     timestamptz,
    UNIQUE (gateway, event_id)
);

CREATE TABLE refund (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id  uuid NOT NULL REFERENCES payment(id),
    amount      numeric(12,2) NOT NULL CHECK (amount > 0),
    reason      text NOT NULL,
    status      text NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','APPROVED','PROCESSED','REJECTED')),
    requested_by uuid REFERENCES app_user(id),
    approved_by  uuid REFERENCES app_user(id),
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 10. Communication
-- ---------------------------------------------------------------------
CREATE TABLE message_template (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             uuid NOT NULL REFERENCES school(id),
    code                  text NOT NULL,            -- STAGE_APPLICATION, STAGE_ADMITTED, FOLLOWUP_REMINDER...
    channel               text NOT NULL CHECK (channel IN ('WHATSAPP','SMS','EMAIL')),
    language              text NOT NULL DEFAULT 'en',
    body                  text NOT NULL,
    provider_template_id  text,                     -- WhatsApp Business pre-approved template
    approval_status       text NOT NULL DEFAULT 'DRAFT' CHECK (approval_status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED')),
    UNIQUE (school_id, code, channel, language)
);

CREATE TABLE communication_log (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id       uuid REFERENCES application(id),
    lead_id              uuid REFERENCES lead(id),
    template_id          uuid REFERENCES message_template(id),
    channel              text NOT NULL,
    to_address           text NOT NULL,
    status               text NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','SENT','DELIVERED','READ','FAILED')),
    provider_message_id  text,
    error                text,
    created_at           timestamptz NOT NULL DEFAULT now(),
    sent_at              timestamptz
);
CREATE INDEX ix_comm_app ON communication_log (application_id, created_at DESC);

-- ---------------------------------------------------------------------
-- 11. History, audit, outbox (append-only where noted)
-- ---------------------------------------------------------------------
CREATE TABLE stage_history (
    id             bigserial PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES application(id),
    from_stage_id  uuid REFERENCES stage_definition(id),
    to_stage_id    uuid NOT NULL REFERENCES stage_definition(id),
    actor_type     text NOT NULL CHECK (actor_type IN ('GUARDIAN','STAFF','SYSTEM')),
    actor_id       text,
    reason         text,
    at             timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_stage_hist_app ON stage_history (application_id, at);

CREATE TABLE audit_log (
    id           bigserial PRIMARY KEY,
    entity_type  text NOT NULL,
    entity_id    text NOT NULL,
    action       text NOT NULL,
    actor_id     text,
    before_data  jsonb,
    after_data   jsonb,
    ip           inet,
    at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id, at);

-- Transactional outbox: written in the same transaction as the state change,
-- relayed to SQS/SNS/Kafka by a poller (or CDC).
CREATE TABLE outbox_event (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  text NOT NULL,
    aggregate_id    uuid NOT NULL,
    event_type      text NOT NULL,
    payload         jsonb NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    published_at    timestamptz,
    attempts        int NOT NULL DEFAULT 0
);
CREATE INDEX ix_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

-- Make history and audit tables immutable
CREATE OR REPLACE FUNCTION forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END $$;

CREATE TRIGGER trg_stage_history_immutable BEFORE UPDATE OR DELETE ON stage_history
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_audit_log_immutable BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Circular reference lead <-> application
ALTER TABLE lead ADD CONSTRAINT fk_lead_application
    FOREIGN KEY (converted_application_id) REFERENCES application(id);

-- ---------------------------------------------------------------------
-- 12. Reporting views
-- ---------------------------------------------------------------------
-- How many distinct applications ever reached each stage (funnel)
CREATE VIEW v_stage_funnel AS
SELECT a.academic_year_id,
       a.class_config_id,
       sd.code      AS stage_code,
       sd.sequence  AS stage_sequence,
       count(DISTINCT sh.application_id) AS reached
FROM stage_history sh
JOIN stage_definition sd ON sd.id = sh.to_stage_id
JOIN application a       ON a.id  = sh.application_id
GROUP BY a.academic_year_id, a.class_config_id, sd.code, sd.sequence;

-- Seats left per class
CREATE VIEW v_seat_availability AS
SELECT c.id AS class_config_id,
       c.display_name,
       c.seats_total,
       count(s.id) FILTER (WHERE s.status IN ('HELD','CONFIRMED')) AS seats_used,
       c.seats_total - count(s.id) FILTER (WHERE s.status IN ('HELD','CONFIRMED')) AS seats_left
FROM class_config c
LEFT JOIN seat_allocation s ON s.class_config_id = c.id
GROUP BY c.id, c.display_name, c.seats_total;

-- Source-wise conversion
CREATE VIEW v_source_conversion AS
SELECT l.school_id, l.academic_year_id, l.source,
       count(*)                                        AS enquiries,
       count(l.converted_application_id)               AS applications,
       count(*) FILTER (WHERE a.admission_no IS NOT NULL) AS admitted
FROM lead l
LEFT JOIN application a ON a.id = l.converted_application_id
GROUP BY l.school_id, l.academic_year_id, l.source;
