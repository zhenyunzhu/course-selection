package com.cs.enroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.enroll.entity.OutboxMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OutboxMapper extends BaseMapper<OutboxMessage> {

    @Select("SELECT * FROM t_outbox_message WHERE status = 0 AND next_retry_time <= NOW() " +
            "ORDER BY id LIMIT #{limit}")
    List<OutboxMessage> selectPending(@Param("limit") int limit);
}
