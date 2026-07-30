package com.dochelper.knowledge.infrastructure.persistence.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochelper.knowledge.infrastructure.persistence.entity.KnowledgeChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识库切片 Mapper。
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkEntity> {

    /**
     * 使用 MySQL 字符串匹配召回包含关键词的候选切片。
     */
    @Select("""
            SELECT c.*
            FROM knowledge_chunk c
            JOIN knowledge_document d ON d.id = c.document_id
            WHERE c.project_id = #{projectId}
              AND d.deleted = 0
              AND d.status = 'INDEXED'
              AND (
                    LOWER(c.content) LIKE CONCAT('%', LOWER(#{term}), '%')
                    OR LOWER(COALESCE(c.section_title, '')) LIKE CONCAT('%', LOWER(#{term}), '%')
                  )
            ORDER BY
              CASE
                WHEN LOWER(COALESCE(c.section_title, '')) LIKE CONCAT('%', LOWER(#{term}), '%')
                THEN 0 ELSE 1
              END,
              c.chunk_index ASC
            LIMIT #{limit}
            """)
    List<KnowledgeChunkEntity> keywordSearch(
            @Param("projectId") Long projectId,
            @Param("term") String term,
            @Param("limit") int limit
    );
}
