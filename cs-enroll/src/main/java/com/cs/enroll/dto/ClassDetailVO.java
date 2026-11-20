package com.cs.enroll.dto;

import lombok.Data;

import java.util.List;

/**
 * 教学班详情（course 侧返回）。
 *
 * 微服务里两个服务各自持有一份 DTO 是正常的：契约靠接口文档 / Schema 保证，
 * 不要让服务因为「共享实体类」而耦合在一起（那是伪微服务）。
 */
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
    private List<String> schedules;
}
