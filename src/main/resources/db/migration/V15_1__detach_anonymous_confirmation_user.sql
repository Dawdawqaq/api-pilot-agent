-- 删除登录表前先解除确认记录对用户表的外键；保留历史确认人标识用于审计。
-- 该过渡迁移位于 V16 之前，避免修改已有迁移文件的校验和。
SET @confirmation_user_fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'agent_confirmation'
      AND CONSTRAINT_NAME = 'fk_agent_confirmation_decided_user'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @detach_confirmation_user = IF(
    @confirmation_user_fk_exists > 0,
    'ALTER TABLE agent_confirmation DROP FOREIGN KEY fk_agent_confirmation_decided_user',
    'SELECT 1'
);
PREPARE detach_confirmation_user_statement FROM @detach_confirmation_user;
EXECUTE detach_confirmation_user_statement;
DEALLOCATE PREPARE detach_confirmation_user_statement;
