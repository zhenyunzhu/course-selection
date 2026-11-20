package com.cs.enroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 选课单（enroll 侧的事实）。status：1 已选 / 0 已退。
 *
 * 字段留了快照（course_name/credit/schedule_snapshot）：
 * 规则校验和退课都不需要再远程查课程服务，跨服务调用次数大幅下降。
 */
@Data
@TableName("t_enrollment")
public class Enrollment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String enrollNo;
    private Long studentId;
    private Long classId;
    private Long courseId;
    private String courseName;
    private Integer credit;
    /** 指向 course 侧的预占记录，退课时要用它 Cancel */
    private String reserveId;
    /** 形如 "1:3-4|3:1-2"，用于时间冲突检测 */
    private String scheduleSnapshot;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
