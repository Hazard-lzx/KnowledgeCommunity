package com.knowledgecommunity.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgecommunity.modules.user.entity.EventOutbox;
import org.apache.ibatis.annotations.Mapper;

/** 事件发件箱 Mapper */
@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {
}
