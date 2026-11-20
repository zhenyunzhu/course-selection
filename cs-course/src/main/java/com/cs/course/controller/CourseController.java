package com.cs.course.controller;

import com.cs.common.Result;
import com.cs.course.entity.Course;
import com.cs.course.entity.CourseClass;
import com.cs.course.service.CapacityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 对外：课程浏览（走网关，需登录）。 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CapacityService capacityService;

    @GetMapping
    public Result<List<Course>> list() {
        return Result.ok(capacityService.listCourses());
    }

    @GetMapping("/{id}")
    public Result<Course> detail(@PathVariable Long id) {
        return Result.ok(capacityService.getCourse(id));
    }

    /** 某门课开出来的教学班（含余量） */
    @GetMapping("/{id}/classes")
    public Result<List<CourseClass>> classes(@PathVariable Long id) {
        return Result.ok(capacityService.listClasses(id));
    }
}
