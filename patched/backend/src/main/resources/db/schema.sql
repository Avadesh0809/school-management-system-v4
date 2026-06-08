-- MySQL schema (Hibernate also auto-creates; this is for manual setup)
CREATE DATABASE IF NOT EXISTS school_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE school_db;

-- All tables are created by Hibernate ddl-auto=update on first run.
-- This file documents required indexes for production tuning.

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_att_date ON attendance(date);
CREATE INDEX IF NOT EXISTS idx_student_class ON students(class_id);
CREATE INDEX IF NOT EXISTS idx_fee_student ON fees(student_id);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_logs(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_file_owner ON file_metadata(owner_type, owner_id);
