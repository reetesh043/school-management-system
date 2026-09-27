--liquibase formatted sql

--changeset erp:005-attendance-null-status-fix
--comment Remove legacy attendance rows without a status and prevent future NULL statuses. An unmarked student is represented by no attendance row.

DELETE FROM attendance_record WHERE status IS NULL;
ALTER TABLE attendance_record ALTER COLUMN status SET NOT NULL;

--rollback ALTER TABLE attendance_record ALTER COLUMN status DROP NOT NULL;
