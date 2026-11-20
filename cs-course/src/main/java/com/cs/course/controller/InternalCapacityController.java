package com.cs.course.controller;

import com.cs.common.Result;
import com.cs.course.dto.ClassDetailVO;
import com.cs.course.dto.ReserveRequest;
import com.cs.course.dto.ReserveResult;
import com.cs.course.service.CapacityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 内部接口（仅供 enroll-service 通过 OpenFeign 调用，不暴露给前端）。
 *
 * 生产环境应该在网关或网络层做隔离：/internal/** 只允许集群内访问。
 * 这里简化成「网关不路由 /internal/**」。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalCapacityController {

    private final CapacityService capacityService;

    @GetMapping("/classes/{classId}")
    public Result<ClassDetailVO> classDetail(@PathVariable Long classId) {
        return Result.ok(capacityService.classDetail(classId));
    }

    /** Try：预占容量 */
    @PostMapping("/capacity/reserve")
    public Result<ReserveResult> reserve(@Valid @RequestBody ReserveRequest req) {
        return Result.ok(capacityService.reserve(req));
    }

    /** Confirm：确认预占（预占转正式占用） */
    @PostMapping("/capacity/{reserveId}/confirm")
    public Result<Void> confirm(@PathVariable String reserveId) {
        capacityService.confirm(reserveId);
        return Result.ok();
    }

    /** Cancel：取消预占并回补容量 */
    @PostMapping("/capacity/{reserveId}/cancel")
    public Result<Void> cancel(@PathVariable String reserveId) {
        capacityService.cancel(reserveId);
        return Result.ok();
    }

    /** Release：退课释放（处理已确认的预占） */
    @PostMapping("/capacity/{reserveId}/release")
    public Result<Void> release(@PathVariable String reserveId) {
        capacityService.release(reserveId);
        return Result.ok();
    }

    /** 对账：释放超时未确认的预占 */
    @PostMapping("/capacity/release-expired")
    public Result<Map<String, Integer>> releaseExpired(@RequestParam(defaultValue = "200") int limit) {
        return Result.ok(Map.of("released", capacityService.releaseExpired(limit)));
    }
}
