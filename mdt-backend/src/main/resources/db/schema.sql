-- MDT专家联合会诊系统 - 数据库初始化脚本
-- PostgreSQL

-- 创建数据库
-- CREATE DATABASE mdt_db;

-- 科室表
CREATE TABLE IF NOT EXISTS mdt_department (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 专家表
CREATE TABLE IF NOT EXISTS mdt_expert (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    department_id BIGINT NOT NULL,
    title VARCHAR(20),
    specialty VARCHAR(500),
    status VARCHAR(20) DEFAULT 'OFFLINE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 会诊单表
CREATE TABLE IF NOT EXISTS mdt_consultation (
    id BIGSERIAL PRIMARY KEY,
    consultation_no VARCHAR(32) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    patient_name VARCHAR(50),
    patient_info VARCHAR(1000),
    description VARCHAR(2000),
    initiator_id BIGINT NOT NULL,
    initiator_name VARCHAR(50),
    room_id VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 会诊专家关联表
CREATE TABLE IF NOT EXISTS mdt_consultation_expert (
    id BIGSERIAL PRIMARY KEY,
    consultation_id BIGINT NOT NULL,
    expert_id BIGINT NOT NULL,
    expert_name VARCHAR(50),
    department_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    joined_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_consultation_initiator ON mdt_consultation(initiator_id);
CREATE INDEX IF NOT EXISTS idx_consultation_status ON mdt_consultation(status);
CREATE INDEX IF NOT EXISTS idx_consultation_room ON mdt_consultation(room_id);
CREATE INDEX IF NOT EXISTS idx_consultation_expert_consultation ON mdt_consultation_expert(consultation_id);
CREATE INDEX IF NOT EXISTS idx_consultation_expert_expert ON mdt_consultation_expert(expert_id);
CREATE INDEX IF NOT EXISTS idx_expert_department ON mdt_expert(department_id);
CREATE INDEX IF NOT EXISTS idx_expert_status ON mdt_expert(status);

COMMENT ON TABLE mdt_department IS '科室表';
COMMENT ON TABLE mdt_expert IS '专家表';
COMMENT ON TABLE mdt_consultation IS '会诊单表';
COMMENT ON TABLE mdt_consultation_expert IS '会诊专家关联表';
