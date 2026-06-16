package com.knowledgecommunity.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgecommunity.modules.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper，基于 MyBatis-Plus BaseMapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
