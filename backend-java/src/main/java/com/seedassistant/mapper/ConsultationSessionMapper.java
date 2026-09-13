package com.seedassistant.mapper;

import com.seedassistant.entity.ConsultationSession;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ConsultationSessionMapper extends BaseMapper<ConsultationSession> {
    @Insert("INSERT INTO consultation_session(id,title,notes,user_id) VALUES(#{id},#{title},#{notes},#{userId})")
    int insertOwned(@Param("id") String id, @Param("title") String title, @Param("notes") String notes, @Param("userId") String userId);
    @Select("SELECT * FROM consultation_session WHERE user_id=#{userId} AND (#{keyword}='' OR LOCATE(#{keyword},title)>0) "
            + "AND (#{status} IS NULL OR status=#{status}) ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ConsultationSession> searchOwned(@Param("userId") String userId, @Param("keyword") String keyword,
            @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);
    // 仅用于经过核实的内部归属迁移，不能把已归属会话转给另一用户。
    @Update("UPDATE consultation_session SET user_id=#{userId},version=version+1,updated_at=UTC_TIMESTAMP(6) "
            + "WHERE id=#{id} AND version=#{version} AND user_id IS NULL")
    int assignUnowned(@Param("id") String id, @Param("userId") String userId, @Param("version") long version);
    @Insert("INSERT INTO consultation_session(id,title,notes) VALUES(#{id},#{title},#{notes})")
    int insertRow(@Param("id") String id, @Param("title") String title, @Param("notes") String notes);
    @Select("SELECT * FROM consultation_session WHERE (#{keyword}='' OR LOCATE(#{keyword},title)>0) "
            + "AND (#{status} IS NULL OR status=#{status}) ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ConsultationSession> search(@Param("keyword") String keyword,@Param("status") String status,@Param("limit") int limit,@Param("offset") int offset);
    @Update("UPDATE consultation_session SET title=#{title},notes=#{notes},version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND version=#{version}")
    int updateVersioned(@Param("id") String id, @Param("title") String title, @Param("notes") String notes, @Param("version") long version);
    @Update("UPDATE consultation_session SET status=#{status},version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND version=#{version}")
    int status(@Param("id") String id,@Param("status") String status,@Param("version") long version);
    @Delete("DELETE FROM consultation_session WHERE id=#{id} AND version=#{version}")
    int deleteVersioned(@Param("id") String id,@Param("version") long version);
}
