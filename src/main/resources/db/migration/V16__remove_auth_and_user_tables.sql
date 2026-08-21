-- 移除开源免登录模式下不再需要的成员与用户表
DROP TABLE IF EXISTS project_member;
DROP TABLE IF EXISTS app_user;

UPDATE sys_setting
SET config_value = '16',
    description = 'DocHelper 数据库结构版本（移除认证与用户表）'
WHERE config_key = 'application.schema.version';
