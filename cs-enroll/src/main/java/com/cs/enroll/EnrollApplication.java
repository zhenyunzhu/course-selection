package com.cs.enroll;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 选课服务：规则校验 + 预占编排 + 选课单 + Outbox。 */
@EnableScheduling
@EnableFeignClients(basePackages = "com.cs.enroll.feign")
@MapperScan("com.cs.enroll.mapper")
@SpringBootApplication(scanBasePackages = {"com.cs.enroll", "com.cs.common"})
public class EnrollApplication {
    public static void main(String[] args) {
        SpringApplication.run(EnrollApplication.class, args);
    }
}
