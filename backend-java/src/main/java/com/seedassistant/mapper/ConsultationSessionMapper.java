package com.seedassistant.mapper;

import com.seedassistant.entity.ConsultationSession;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ConsultationSessionMapper {
    @Insert("INSERT INTO consultation_session(id,title,notes) VALUES(#{id},#{title},#{notes})")
    int insert(@Param("id") String id, @Param("title") String title, @Param("notes") String notes);
    @Select("SELECT * FROM consultation_session WHERE id=#{id}")
    ConsultationSession find(String id);
    @Select("SELECT * FROM consultation_session WHERE (#{keyword}='' OR LOCATE(#{keyword},title)>0) "
            + "AND (#{status} IS NULL OR status=#{status}) ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ConsultationSession> search(@Param("keyword") String keyword,@Param("status") String status,@Param("limit") int limit,@Param("offset") int offset);
    @Update("UPDATE consultation_session SET title=#{title},notes=#{notes},version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND version=#{version}")
    int update(@Param("id") String id, @Param("title") String title, @Param("notes") String notes, @Param("version") long version);
    @Update("UPDATE consultation_session SET status=#{status},version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND version=#{version}")
    int status(@Param("id") String id,@Param("status") String status,@Param("version") long version);
    @Delete("DELETE FROM consultation_session WHERE id=#{id} AND version=#{version}")
    int delete(@Param("id") String id,@Param("version") long version);
}
