-- 长期模型密钥没有任务级过期时间，替换时由配置服务删除旧密钥。
ALTER TABLE runtime_secret MODIFY expires_at DATETIME(3) NULL;
ALTER TABLE sys_setting MODIFY config_value TEXT NOT NULL;
