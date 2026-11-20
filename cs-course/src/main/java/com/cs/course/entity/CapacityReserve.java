package com.cs.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 容量预占记录（TCC 的「事务凭证」）。
 *
 * 状态机：0 预占中 → 1 已确认 / 2 已取消 / 3 超时释放
 * 只有 0 能流转到 1/2，这保证了「重复确认」「取消后再确认」不会改坏容量。
 */
@Data
@TableName("t_capacity_reserve")
public class CapacityReserve {
    /** 主键就是 reserveId，由上游生成 —— 上游重试时天然幂等 */
    @TableId(type = IdType.INPUT)
    private String reserveId;
    private Long classId;
    private Long studentId;
    private Integer status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
