package com.cs.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 教学班：容量在这里。
 *
 * remain 是「当前可选余量」。扣减必须用条件更新
 * (remain > 0 才能减)，绝不能用「先查后减」。
 */
@Data
@TableName("t_class")
public class CourseClass {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private String name;
    private String teacher;
    private Integer capacity;
    private Integer remain;
    private Integer status;
}
