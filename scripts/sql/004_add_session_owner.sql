-- One atomic ALTER. DatabaseSetup checks metadata before a repeated initialization.
-- Existing rows keep NULL ownership. Never guess a customer for historical data.
USE seed_assistant;
ALTER TABLE consultation_session
    ADD COLUMN user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD INDEX idx_session_user_created(user_id,created_at DESC,id DESC),
    ADD CONSTRAINT fk_session_user FOREIGN KEY(user_id) REFERENCES user_account(id) ON DELETE RESTRICT;
