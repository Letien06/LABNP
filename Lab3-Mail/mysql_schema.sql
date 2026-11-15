CREATE TABLE IF NOT EXISTS users (
    email VARCHAR(320) PRIMARY KEY,
    password TEXT NOT NULL,
    created_at VARCHAR(64) NOT NULL,
    display_name TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sender TEXT NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    subject LONGTEXT NOT NULL,
    body LONGTEXT NOT NULL,
    raw LONGBLOB NOT NULL,
    created_at VARCHAR(64) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_messages_recipient (recipient, deleted, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS mailbox_read (
    account VARCHAR(320) NOT NULL,
    message_id BIGINT NOT NULL,
    PRIMARY KEY (account, message_id),
    FOREIGN KEY (account) REFERENCES users(email) ON DELETE CASCADE,
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS drafts (
    account VARCHAR(320) NOT NULL,
    id VARCHAR(36) NOT NULL,
    recipient TEXT NOT NULL,
    subject TEXT NOT NULL,
    body LONGTEXT NOT NULL,
    links LONGTEXT NOT NULL,
    updated_at VARCHAR(64) NOT NULL,
    PRIMARY KEY (account, id),
    FOREIGN KEY (account) REFERENCES users(email) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS sqlite_migration (
    slot TINYINT NOT NULL PRIMARY KEY,
    source_digest CHAR(64),
    user_count BIGINT,
    message_count BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
