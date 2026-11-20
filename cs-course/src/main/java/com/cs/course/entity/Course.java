package com.cs.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 课程（知识维度）。教学班（容量维度）在 t_class。 */
@Data
@TableName("t_course")
public class Course {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private Integer credit;
    /** 先修课 id，为空表示无先修要求 */
    private Long prerequisiteId;
    private Integer status;
}
