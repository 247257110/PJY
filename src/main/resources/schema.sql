CREATE DATABASE IF NOT EXISTS pjy DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE pjy;

CREATE TABLE IF NOT EXISTS work_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_name VARCHAR(200) COMMENT '公司名称',
  name VARCHAR(100) COMMENT '姓名',
  project_name VARCHAR(200) COMMENT '项目人员/项目名称',
  actual_start_date DATE COMMENT '实际开始时间',
  actual_end_date DATE COMMENT '实际结束时间',
  actual_days DECIMAL(10,2) COMMENT '实际投入(人天)',
  standard_days DECIMAL(10,2) COMMENT '实施投入(标准人天)',
  work_content TEXT COMMENT '工作内容',
  source_file VARCHAR(500) COMMENT '来源文件名',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验收材料基础库';

CREATE TABLE IF NOT EXISTS temp_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id VARCHAR(64) NOT NULL COMMENT '上传批次ID',
  company_name VARCHAR(200) COMMENT '公司名称',
  name VARCHAR(100) COMMENT '姓名',
  project_name VARCHAR(200) COMMENT '项目人员/项目名称',
  actual_start_date DATE COMMENT '实际开始时间',
  actual_end_date DATE COMMENT '实际结束时间',
  actual_days DECIMAL(10,2) COMMENT '实际投入(人天)',
  standard_days DECIMAL(10,2) COMMENT '实施投入(标准人天)',
  work_content TEXT COMMENT '工作内容',
  source_file VARCHAR(500) COMMENT '来源文件名',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  INDEX idx_batch_id (batch_id),
  INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验收材料临时表';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
  role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
  remark VARCHAR(200) COMMENT '备注',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  password VARCHAR(200) NOT NULL COMMENT '密码(BCrypt)',
  real_name VARCHAR(50) COMMENT '真实姓名',
  email VARCHAR(100) COMMENT '邮箱',
  phone VARCHAR(20) COMMENT '手机号',
  status TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 初始化角色
INSERT IGNORE INTO sys_role (role_name, role_code, remark) VALUES
('超级管理员', 'ADMIN', '系统超级管理员'),
('普通用户', 'USER', '普通操作用户');

-- 初始化管理员账号 密码: admin123
INSERT IGNORE INTO sys_user (username, password, real_name, status) VALUES
('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 1);

-- 绑定管理员角色
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username='admin' AND r.role_code='ADMIN';

-- 机构表
CREATE TABLE IF NOT EXISTS sys_org (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  org_name VARCHAR(100) NOT NULL COMMENT '机构名称',
  org_code VARCHAR(50) UNIQUE COMMENT '机构编码',
  parent_id BIGINT DEFAULT 0 COMMENT '父机构ID，0为顶级',
  sort INT DEFAULT 0 COMMENT '排序',
  status TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',
  remark VARCHAR(200) COMMENT '备注',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机构表';

-- 菜单表
CREATE TABLE IF NOT EXISTS sys_menu (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  menu_name VARCHAR(50)  NOT NULL COMMENT '菜单名称',
  menu_key  VARCHAR(50)  NOT NULL UNIQUE COMMENT '菜单标识',
  path      VARCHAR(100) COMMENT '前端路由路径',
  icon      VARCHAR(50)  COMMENT '图标',
  sort      INT DEFAULT 0 COMMENT '排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

-- 角色菜单关联表
CREATE TABLE IF NOT EXISTS sys_role_menu (
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- 用户表增加机构字段
ALTER TABLE sys_user ADD org_id BIGINT DEFAULT NULL COMMENT '所属机构ID';

-- 验收材料基础库增加机构字段
ALTER TABLE work_record
    ADD COLUMN org_id   BIGINT       DEFAULT NULL COMMENT '机构ID'   AFTER source_file,
    ADD COLUMN org_name VARCHAR(200) DEFAULT NULL COMMENT '机构名称' AFTER org_id;

-- 核验临时表增加机构字段
ALTER TABLE temp_record
    ADD COLUMN org_id   BIGINT       DEFAULT NULL COMMENT '机构ID'   AFTER source_file,
    ADD COLUMN org_name VARCHAR(200) DEFAULT NULL COMMENT '机构名称' AFTER org_id;

-- 初始化菜单数据
INSERT IGNORE INTO sys_menu (menu_name, menu_key, path, icon, sort) VALUES
('验收材料基础库',   'base-lib',  '/base-lib',  'DataBoard',       1),
('项目验收材料校验', 'verify',    '/verify',    'DocumentChecked', 2),
('用户管理',         'sys:user',  '/sys/user',  'User',            3),
('角色管理',         'sys:role',  '/sys/role',  'UserFilled',      4),
('机构管理',         'sys:org',   '/sys/org',   'OfficeBuilding',  5);

-- ADMIN 角色拥有全部菜单
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r, sys_menu m WHERE r.role_code = 'ADMIN';

-- USER 角色只有核心功能菜单
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r, sys_menu m
WHERE r.role_code = 'USER' AND m.menu_key IN ('base-lib', 'verify');



ALTER TABLE work_record
    ADD COLUMN org_id   BIGINT       DEFAULT NULL COMMENT '机构ID'   AFTER source_file,
    ADD COLUMN org_name VARCHAR(200) DEFAULT NULL COMMENT '机构名称' AFTER org_id;

-- 考勤校对字段
ALTER TABLE work_record
    ADD COLUMN IF NOT EXISTS attendance_verified TINYINT(1) NULL DEFAULT NULL
    COMMENT '考勤校对: NULL=未校对, 1=通过, 0=不通过'
    AFTER org_name;

-- 考勤记录表
CREATE TABLE IF NOT EXISTS attendance_record (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  work_record_id BIGINT       NOT NULL COMMENT '关联work_record.id',
  project_name   VARCHAR(200) COMMENT '项目名称',
  name           VARCHAR(100) COMMENT '姓名',
  check_date     DATE         NOT NULL COMMENT '考勤日期',
  morning        VARCHAR(10)  COMMENT '上午:有/无',
  afternoon      VARCHAR(10)  COMMENT '下午:有/无',
  source_file    VARCHAR(500) COMMENT '来源文件名',
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_work_record_id (work_record_id),
  INDEX idx_name_date (name, check_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤记录表';

-- 考勤记录临时表
CREATE TABLE IF NOT EXISTS temp_attendance_record (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  temp_record_id BIGINT       COMMENT '关联temp_record.id（按姓名匹配，可为null）',
  batch_id       VARCHAR(64)  NOT NULL COMMENT '上传批次ID',
  project_name   VARCHAR(200) COMMENT '项目名称',
  name           VARCHAR(100) COMMENT '姓名',
  check_date     DATE         NOT NULL COMMENT '考勤日期',
  morning        VARCHAR(10)  COMMENT '上午:有/无',
  afternoon      VARCHAR(10)  COMMENT '下午:有/无',
  source_file    VARCHAR(500) COMMENT '来源文件名',
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_batch_id (batch_id),
  INDEX idx_temp_record_id (temp_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤记录临时表';

-- 已有库补字段
ALTER TABLE attendance_record
    ADD COLUMN IF NOT EXISTS morning   VARCHAR(10) COMMENT '上午:有/无' AFTER check_date,
    ADD COLUMN IF NOT EXISTS afternoon VARCHAR(10) COMMENT '下午:有/无' AFTER morning;
