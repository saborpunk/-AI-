package com.seedassistant.consultation;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ConsultationMapper {
    String COLUMNS = "id, question, batch_code, status, draft_result, final_answer, version, created_at, updated_at, confirmed_at";

    @Insert("INSERT INTO consultation(id, question, batch_code) VALUES(#{id}, #{question}, #{batchCode})")
    int insert(@Param("id") String id, @Param("question") String question, @Param("batchCode") String batchCode);

    @Select("SELECT " + COLUMNS + " FROM consultation WHERE id=#{id}")
    Consultation.Row find(String id);

    @Select("SELECT " + COLUMNS + " FROM consultation ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Consultation.Row> history(@Param("limit") int limit, @Param("offset") int offset);

    // version 条件使并发更新只有一个生效；所有用户输入均绑定参数，不拼接 SQL。
    @Update("UPDATE consultation SET draft_result=#{result}, status='DRAFT_READY', version=version+1, "
            + "updated_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND version=#{version} AND status='PENDING'")
    int saveDraft(@Param("id") String id, @Param("version") long version, @Param("result") String result);

    @Update("UPDATE consultation SET final_answer=#{answer}, status=#{status}, version=version+1, "
            + "updated_at=UTC_TIMESTAMP(6), confirmed_at=CASE WHEN #{status}='CONFIRMED' THEN UTC_TIMESTAMP(6) ELSE NULL END "
            + "WHERE id=#{id} AND version=#{version} AND status IN ('PENDING','DRAFT_READY')")
    int review(@Param("id") String id, @Param("version") long version,
               @Param("answer") String answer, @Param("status") String status);
}
