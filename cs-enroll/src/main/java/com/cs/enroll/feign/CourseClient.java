package com.cs.enroll.feign;

import com.cs.common.Result;
import com.cs.enroll.dto.ClassDetailVO;
import com.cs.enroll.dto.ReserveRequest;
import com.cs.enroll.dto.ReserveResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 调用 course 服务的内部接口。
 *
 * 超时与重试策略要按「接口语义」定：
 *  - 查询类（classDetail）可以重试
 *  - Try / Confirm 靠 reserveId 幂等，重试安全
 *  - 但绝不配置「自动重试」：由业务层显式决定，避免集群抖动时放大流量
 */
@FeignClient(name = "cs-course", path = "/internal")
public interface CourseClient {

    @GetMapping("/classes/{classId}")
    Result<ClassDetailVO> classDetail(@PathVariable("classId") Long classId);

    @PostMapping("/capacity/reserve")
    Result<ReserveResult> reserve(@RequestBody ReserveRequest req);

    @PostMapping("/capacity/{reserveId}/confirm")
    Result<Void> confirm(@PathVariable("reserveId") String reserveId);

    @PostMapping("/capacity/{reserveId}/cancel")
    Result<Void> cancel(@PathVariable("reserveId") String reserveId);

    /** 退课释放：处理「已确认」的预占 */
    @PostMapping("/capacity/{reserveId}/release")
    Result<Void> release(@PathVariable("reserveId") String reserveId);

    @PostMapping("/capacity/release-expired")
    Result<Map<String, Object>> releaseExpired(@RequestParam("limit") int limit);
}
