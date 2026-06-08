-- Sample data is seeded programmatically by DataSeeder on startup.
-- Default accounts:
--   superadmin / Admin@123
--   admin      / Admin@123
INSERT IGNORE INTO roles(name, description, created_at, updated_at) VALUES
 ('SUPER_ADMIN','Super Admin', NOW(), NOW()),
 ('ADMIN','Administrator', NOW(), NOW()),
 ('TEACHER','Teacher', NOW(), NOW()),
 ('STUDENT','Student', NOW(), NOW()),
 ('PARENT','Parent', NOW(), NOW()),
 ('ACCOUNTANT','Accountant', NOW(), NOW()),
 ('LIBRARIAN','Librarian', NOW(), NOW());
