package com.cs.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.course.entity.CapacityReserve;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CapacityReserveMapper extends BaseMapper<CapacityReserve> {

    /**
     * CAS 改状态：只有「当前状态 = from」才能改成 to。
     * 影响行数为 0，说明状态已经变了（重复确认 / 已取消），调用方据此走幂等分支。
     */
    @Update("UPDATE t_capacity_reserve SET status = #{to}, update_time = NOW() " +
            "WHERE reserve_id = #{reserveId} AND status = #{from}")
    int casStatus(@Param("reserveId") String reserveId,
                  @Param("from") int from,
                  @Param("to") int to);

    /** 超时未确认的预占：对账任务捞这些去释放 */
    @Select("SELECT reserve_id FROM t_capacity_reserve " +
            "WHERE status = 0 AND expire_time < NOW() LIMIT #{limit}")
    List<String> selectExpired(@Param("limit") int limit);
}
