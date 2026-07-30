package com.dochelper.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.knowledge.infrastructure.persistence.entity.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper。
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {
}
