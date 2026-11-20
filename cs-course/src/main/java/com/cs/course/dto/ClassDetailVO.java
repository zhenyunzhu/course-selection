package com.cs.course.dto;

import lombok.Data;

import java.util.List;

/** 教学班详情：选课侧做规则校验时要用的全部信息，一次远程调用拿全（避免 N+1）。 */
@Data
public class ClassDetailVO {
    private Long classId;
    private Long courseId;
    private String courseName;
    private Integer credit;
    private Long prerequisiteId;
    private String teacher;
    private Integer capacity;
    private Integer remain;
    /** 形如 ["1:3-4","3:1-2"] */
    private List<String> schedules;
}
