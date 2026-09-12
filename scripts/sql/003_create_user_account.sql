-- V2 adds an empty user table. No default account or password is seeded.
USE seed_assistant;
CREATE TABLE IF NOT EXISTS user_account (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    username VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_hash VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT uk_user_username UNIQUE(username),
    CHECK (REGEXP_LIKE(username, '^[a-z0-9_]{3,32}$', 'c')),
    CHECK (CHAR_LENGTH(password_hash) = 60 AND LEFT(password_hash,4) = '$2a$'),
    CHECK (CHAR_LENGTH(TRIM(display_name)) > 0),
    CHECK (role IN ('CUSTOMER','MERCHANT')),
    CHECK (status IN ('ENABLED','DISABLED')),
    CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
