package com.dochelper.secret.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 加密运行时秘密数据库实体。
 */
@TableName("runtime_secret")
public class RuntimeSecretEntity {
    @TableId(type = IdType.INPUT) private String secretRef;
    private String scopeHash;
    private byte[] encryptedValue;
    private byte[] initializationVector;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public String getScopeHash() { return scopeHash; }
    public void setScopeHash(String scopeHash) { this.scopeHash = scopeHash; }
    public byte[] getEncryptedValue() { return encryptedValue; }
    public void setEncryptedValue(byte[] encryptedValue) { this.encryptedValue = encryptedValue; }
    public byte[] getInitializationVector() { return initializationVector; }
    public void setInitializationVector(byte[] initializationVector) { this.initializationVector = initializationVector; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
