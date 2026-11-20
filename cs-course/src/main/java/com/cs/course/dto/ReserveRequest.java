package com.cs.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Try 请求：reserveId 由上游生成，重试时同一个 id 天然幂等。 */
@Data
public class ReserveRequest {
    @NotBlank
    private String reserveId;
    @NotNull
    private Long classId;
    @NotNull
    private Long studentId;
    /** 预占有效期（秒），不传用服务端默认 */
    private Integer ttlSeconds;
}
