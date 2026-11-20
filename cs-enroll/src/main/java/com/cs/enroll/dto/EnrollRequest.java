package com.cs.enroll.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EnrollRequest {
    @NotNull
    private Long classId;
}
