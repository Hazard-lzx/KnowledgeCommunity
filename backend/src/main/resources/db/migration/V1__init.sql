CREATE TABLE `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    avatar_url VARCHAR(255),
    signature VARCHAR(200) DEFAULT '',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE article (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    summary TEXT,
    cover_url VARCHAR(255),
    status TINYINT DEFAULT 1 COMMENT '0草稿 1已发布',
    tags VARCHAR(255),
    like_count INT DEFAULT 0,
    collect_count INT DEFAULT 0,
    view_count INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status_create (status, create_time)
);

CREATE TABLE user_follow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    follower_id BIGINT NOT NULL,
    followee_id BIGINT NOT NULL,
    status TINYINT DEFAULT 1 COMMENT '1关注 0取消',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_follow (follower_id, followee_id)
);

CREATE TABLE event_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSON NOT NULL,
    status TINYINT DEFAULT 0 COMMENT '0待发送 1已发送 2发送中 3失败',
    retry_count INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status_create (status, create_time)
);
