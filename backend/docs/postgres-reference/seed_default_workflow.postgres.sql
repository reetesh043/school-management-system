-- Seeds the six-stage default workflow plus waitlist / reject side stages.
-- Usage:  SELECT seed_default_workflow('<school-uuid>');
-- Guard and action keys must match the @Component keys in the Spring code.

CREATE OR REPLACE FUNCTION add_transition(
    p_wf uuid, p_from text, p_to text,
    p_roles text[], p_guards text[], p_actions text[]
) RETURNS void LANGUAGE sql AS $$
    INSERT INTO stage_transition (workflow_id, from_stage_id, to_stage_id, allowed_roles, guard_keys, action_keys)
    SELECT p_wf, f.id, t.id, p_roles, p_guards, p_actions
    FROM stage_definition f, stage_definition t
    WHERE f.workflow_id = p_wf AND f.code = p_from
      AND t.workflow_id = p_wf AND t.code = p_to;
$$;

CREATE OR REPLACE FUNCTION seed_default_workflow(p_school_id uuid) RETURNS uuid
LANGUAGE plpgsql AS $$
DECLARE
    wf uuid := gen_random_uuid();
    open_stage text;
BEGIN
    INSERT INTO workflow (id, school_id, name, version, status)
    VALUES (wf, p_school_id, 'Default admission workflow', 1, 'ACTIVE');

    INSERT INTO stage_definition (workflow_id, code, name, sequence, category) VALUES
        (wf, 'ENQUIRY',     'Enquiry',              10, 'OPEN'),
        (wf, 'APPLICATION', 'Application',          20, 'OPEN'),
        (wf, 'REGISTRATION','Registration',         30, 'OPEN'),
        (wf, 'INITIATED',   'Admission initiated',  40, 'OPEN'),
        (wf, 'PAYMENT',     'Payment',              50, 'OPEN'),
        (wf, 'ADMITTED',    'Admitted',             60, 'TERMINAL_SUCCESS'),
        (wf, 'WAITLISTED',  'Waitlisted',           90, 'HOLD'),
        (wf, 'REJECTED',    'Rejected',             99, 'TERMINAL_FAIL');

    -- forward path
    PERFORM add_transition(wf,'ENQUIRY','APPLICATION',
        '{GUARDIAN,COUNSELLOR,FRONT_OFFICE,ADMIN}', '{AGE_ELIGIBLE}', '{NOTIFY}');
    PERFORM add_transition(wf,'APPLICATION','REGISTRATION',
        '{COUNSELLOR,FRONT_OFFICE,ADMIN}', '{DOCS_VERIFIED}', '{NOTIFY}');
    PERFORM add_transition(wf,'REGISTRATION','INITIATED',
        '{ADMISSION_COMMITTEE,ADMIN}', '{ASSESSMENT_PASSED}', '{NOTIFY}');
    PERFORM add_transition(wf,'INITIATED','PAYMENT',
        '{ADMISSION_COMMITTEE,ADMIN}', '{SEAT_AVAILABLE}', '{HOLD_SEAT,ISSUE_INVOICE,NOTIFY}');
    PERFORM add_transition(wf,'PAYMENT','ADMITTED',
        '{SYSTEM,ACCOUNTS,ADMIN}', '{PAYMENT_RECEIVED}', '{CONFIRM_ADMISSION,NOTIFY}');

    -- side paths from every open stage
    FOREACH open_stage IN ARRAY ARRAY['ENQUIRY','APPLICATION','REGISTRATION','INITIATED','PAYMENT'] LOOP
        PERFORM add_transition(wf, open_stage, 'WAITLISTED',
            '{ADMISSION_COMMITTEE,ADMIN}', '{}',
            CASE WHEN open_stage = 'PAYMENT' THEN '{RELEASE_SEAT,NOTIFY}'::text[] ELSE '{NOTIFY}'::text[] END);
        PERFORM add_transition(wf, open_stage, 'REJECTED',
            '{ADMISSION_COMMITTEE,ADMIN}', '{}',
            CASE WHEN open_stage = 'PAYMENT' THEN '{RELEASE_SEAT,NOTIFY}'::text[] ELSE '{NOTIFY}'::text[] END);
    END LOOP;

    -- reopen a waitlisted application back into the funnel
    PERFORM add_transition(wf,'WAITLISTED','INITIATED',
        '{ADMISSION_COMMITTEE,ADMIN}', '{}', '{NOTIFY}');
    PERFORM add_transition(wf,'WAITLISTED','REJECTED',
        '{ADMISSION_COMMITTEE,ADMIN}', '{}', '{NOTIFY}');

    RETURN wf;
END $$;
