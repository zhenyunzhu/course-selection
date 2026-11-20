package com.cs.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Try 结果：status 0 预占中 / 1 已确认 / 2 已取消 / 3 超时释放。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReserveResult {
    private String reserveId;
    private int status;
    private Integer remain;
}
