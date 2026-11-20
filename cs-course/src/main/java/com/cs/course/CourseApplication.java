package com.cs.course;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 课程服务：课程 / 教学班 / 容量预占（TCC 的 T 与 C）。 */
@EnableScheduling
@MapperScan("com.cs.course.mapper")
@SpringBootApplication(scanBasePackages = {"com.cs.course", "com.cs.common"})
public class CourseApplication {
    public static void main(String[] args) {
        SpringApplication.run(CourseApplication.class, args);
    }
}
