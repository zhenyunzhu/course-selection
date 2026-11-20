package com.cs.enroll.dto;

import lombok.Data;

@Data
public class ReserveResult {
    private String reserveId;
    private int status;
    private Integer remain;
}
