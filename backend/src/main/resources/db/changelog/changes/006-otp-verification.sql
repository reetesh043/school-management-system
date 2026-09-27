--liquibase formatted sql

--changeset otp:001-contact-verification
ALTER TABLE student ADD COLUMN guardian_email_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE student ADD COLUMN guardian_phone_verified_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE otp_challenge (
    id                  VARCHAR(36) PRIMARY KEY,
    student_id          BIGINT NOT NULL,
    channel             VARCHAR(10) NOT NULL,
    destination         VARCHAR(180) NOT NULL,
    code_hash           VARCHAR(255) NOT NULL,
    purpose             VARCHAR(40) NOT NULL DEFAULT 'CONTACT_VERIFICATION',
    attempts            INT NOT NULL DEFAULT 0,
    max_attempts        INT NOT NULL DEFAULT 5,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    resend_available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at         TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_otp_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE,
    CONSTRAINT ck_otp_channel CHECK (channel IN ('EMAIL','PHONE'))
);
CREATE INDEX idx_otp_student_channel ON otp_challenge(student_id, channel, created_at);
