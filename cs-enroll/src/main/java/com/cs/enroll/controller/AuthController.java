package com.cs.enroll.controller;

import com.cs.common.Result;
import com.cs.enroll.service.AuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 简化登录：直接传 studentId（生产换成学号密码 + 学校统一认证）。 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Data
    public static class LoginRequest {
        private Long studentId;
        private String name;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        if (req.getStudentId() == null) {
            return Result.fail(1001, "studentId 不能为空");
        }
        String name = req.getName() == null ? "同学" : req.getName();
        String token = authService.login(req.getStudentId(), name);
        return Result.ok(Map.of("token", token, "studentId", req.getStudentId(), "name", name));
    }
}
