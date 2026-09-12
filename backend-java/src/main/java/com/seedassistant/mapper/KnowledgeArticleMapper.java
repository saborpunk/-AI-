package com.seedassistant.mapper;

import com.seedassistant.entity.KnowledgeArticle;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface KnowledgeArticleMapper {
    @Insert("INSERT INTO knowledge_article(id,title,content,category_id) VALUES(#{id},#{title},#{content},#{categoryId})")
    int insert(@Param("id") String id, @Param("title") String title, @Param("content") String content, @Param("categoryId") String categoryId);
    @Select("SELECT * FROM knowledge_article WHERE id=#{id}")
    KnowledgeArticle find(String id);
    @Select("SELECT * FROM knowledge_article WHERE (#{keyword}='' OR LOCATE(#{keyword},title)>0) "
            + "AND (#{status} IS NULL OR status=#{status}) ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<KnowledgeArticle> search(@Param("keyword") String keyword,@Param("status") String status,@Param("limit") int limit,@Param("offset") int offset);
    @Update("UPDATE knowledge_article SET title=#{title},content=#{content},category_id=#{categoryId},version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND version=#{version}")
    int update(@Param("id") String id, @Param("title") String title, @Param("content") String content, @Param("categoryId") String categoryId, @Param("version") long version);
    @Update("UPDATE knowledge_article SET status=#{status},version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND version=#{version}")
    int status(@Param("id") String id,@Param("status") String status,@Param("version") long version);
    @Delete("DELETE FROM knowledge_article WHERE id=#{id} AND version=#{version}")
    int delete(@Param("id") String id,@Param("version") long version);
}
