package com.cs.enroll.controller;

import com.cs.common.Result;
import com.cs.enroll.feign.CourseClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 对账入口：上游主动触发释放超时预占（课程服务自己也有定时兜底）。 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class ReconcileController {

    private final CourseClient courseClient;

    @PostMapping("/reconcile")
    public Result<Object> reconcile() {
        return Result.ok(courseClient.releaseExpired(200).getData());
    }
}
