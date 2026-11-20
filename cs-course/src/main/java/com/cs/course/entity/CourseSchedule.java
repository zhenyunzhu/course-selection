package com.cs.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 排课时间：一个教学班可以有多段。
 * 冲突检测在选课侧做，这里只负责提供数据。
 */
@Data
@TableName("t_class_schedule")
public class CourseSchedule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long classId;
    /** 1-7，周一到周日 */
    private Integer weekday;
    private Integer startSection;
    private Integer endSection;
}
