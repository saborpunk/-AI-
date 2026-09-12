package com.seedassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedassistant.entity.UserAccount;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
    @Select("SELECT * FROM user_account WHERE username=#{username}")
    UserAccount findByUsername(String username);

    // 显式省略角色、状态、时间，统一使用数据库默认值，调用方不能自行创建商家。
    @Insert("INSERT INTO user_account(id,username,password_hash,display_name) "
            + "VALUES(#{id},#{username},#{passwordHash},#{displayName})")
    int insertCustomer(UserAccount account);
}
