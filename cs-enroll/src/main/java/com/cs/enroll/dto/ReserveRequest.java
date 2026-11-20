package com.cs.enroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReserveRequest {
    @NotBlank
    private String reserveId;
    @NotNull
    private Long classId;
    @NotNull
    private Long studentId;
    private Integer ttlSeconds;
}
