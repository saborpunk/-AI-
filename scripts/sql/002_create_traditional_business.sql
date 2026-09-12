-- Additive V1 business schema. Keep the existing consultation table unchanged.
USE seed_assistant;
CREATE TABLE IF NOT EXISTS article_category (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CHECK (status IN ('ENABLED','DISABLED')),
    CHECK (version >= 0),
    INDEX idx_category_created(created_at DESC,id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_article (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CHECK (CHAR_LENGTH(TRIM(content)) > 0),
    CHECK (status IN ('DRAFT','PUBLISHED')),
    CHECK (version >= 0),
    CONSTRAINT fk_article_category FOREIGN KEY(category_id) REFERENCES article_category(id) ON DELETE RESTRICT,
    INDEX idx_article_created(created_at DESC,id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consultation_session (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    notes VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CHECK (status IN ('OPEN','CLOSED')),
    CHECK (version >= 0),
    INDEX idx_session_created(created_at DESC,id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
