package com.seedassistant.mapper;

import com.seedassistant.entity.ArticleCategory;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ArticleCategoryMapper extends BaseMapper<ArticleCategory> {
    @Insert("INSERT INTO article_category(id,name,description) VALUES(#{id},#{name},#{description})")
    int insertRow(@Param("id") String id, @Param("name") String name, @Param("description") String description);
    @Select("SELECT * FROM article_category WHERE (#{keyword}='' OR LOCATE(#{keyword},name)>0) "
            + "AND (#{status} IS NULL OR status=#{status}) ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ArticleCategory> search(@Param("keyword") String keyword, @Param("status") String status,
            @Param("limit") int limit, @Param("offset") int offset);
    @Update("UPDATE article_category SET name=#{name},description=#{description},version=version+1,updated_at=UTC_TIMESTAMP(6) "
            + "WHERE id=#{id} AND version=#{version}")
    int updateVersioned(@Param("id") String id,@Param("name") String name,@Param("description") String description,@Param("version") long version);
    @Update("UPDATE article_category SET status=#{status},version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND version=#{version}")
    int status(@Param("id") String id,@Param("status") String status,@Param("version") long version);
    // The foreign key rejects deletion of a category still referenced by articles.
    @Delete("DELETE FROM article_category WHERE id=#{id} AND version=#{version}")
    int deleteVersioned(@Param("id") String id,@Param("version") long version);
}
