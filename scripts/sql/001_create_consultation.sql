-- Run on the existing localhost:3306 MySQL service, using an authorized account.
-- No service configuration or physical data file operations are performed.
-- This script is additive. Re-running does not clear existing records.
CREATE DATABASE IF NOT EXISTS seed_assistant CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE seed_assistant;

CREATE TABLE IF NOT EXISTS consultation (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    question VARCHAR(2000) NOT NULL,
    batch_code VARCHAR(40) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    draft_result JSON NULL,
    final_answer VARCHAR(4000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    confirmed_at DATETIME(6) NULL,
    CONSTRAINT ck_consultation_status CHECK (status IN ('PENDING', 'DRAFT_READY', 'CONFIRMED')),
    CONSTRAINT ck_consultation_version CHECK (version >= 0),
    CONSTRAINT ck_consultation_question CHECK (CHAR_LENGTH(TRIM(question)) > 0),
    CONSTRAINT ck_consultation_confirmed CHECK (status <> 'CONFIRMED' OR
        (final_answer IS NOT NULL AND CHAR_LENGTH(TRIM(final_answer)) > 0 AND confirmed_at IS NOT NULL)),
    INDEX idx_consultation_created (created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
