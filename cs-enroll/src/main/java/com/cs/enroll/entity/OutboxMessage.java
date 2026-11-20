package com.cs.enroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表（Outbox）。
 *
 * 选课成功要和「发通知」保持一致：不能出现「选上了但没通知」，
 * 也不能「通知发了但事务回滚」。做法是业务数据与消息同一个事务落库，
 * 再由中继任务异步投递 —— 事务提交则消息必在。
 */
@Data
@TableName("t_outbox_message")
public class OutboxMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private String bizKey;
    private String payload;
    /** 0 待投递 / 1 已投递 / 2 死信 */
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime createTime;
}
