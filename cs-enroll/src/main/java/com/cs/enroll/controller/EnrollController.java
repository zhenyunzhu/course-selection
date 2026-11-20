package com.cs.enroll.controller;

import com.cs.common.Result;
import com.cs.enroll.context.StudentContext;
import com.cs.enroll.dto.EnrollRequest;
import com.cs.enroll.entity.Enrollment;
import com.cs.enroll.service.EnrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 对外：选课 / 退课 / 我的课表。 */
@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollController {

    private final EnrollService enrollService;

    /**
     * 选课。X-Idempotent-Token 是客户端生成的一次性凭证（UUID），
     * 5 分钟内重复提交会被幂等闸门挡下。
     */
    @PostMapping
    public Result<Map<String, Object>> enroll(@RequestHeader("X-Idempotent-Token") String idemToken,
                                              @Valid @RequestBody EnrollRequest req) {
        return Result.ok(enrollService.enroll(StudentContext.require(), idemToken, req));
    }

    @DeleteMapping("/{enrollNo}")
    public Result<Void> drop(@PathVariable String enrollNo) {
        enrollService.drop(StudentContext.require(), enrollNo);
        return Result.ok();
    }

    @GetMapping("/my")
    public Result<List<Enrollment>> my() {
        return Result.ok(enrollService.myEnrollments(StudentContext.require()));
    }
}
