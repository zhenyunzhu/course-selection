package com.cs.common;

import lombok.Getter;

/**
 * 业务错误码。
 *
 * 分段规则（面试可讲）：10xx 通用 / 20xx 课程与容量 / 30xx 选课。
 * 分段的意义：看到码就知道是哪个域出的问题，日志排查和前端提示都按段处理。
 */
@Getter
public enum ResultCode {

    OK(0, "ok"),

    INVALID_PARAM(1001, "参数不合法"),
    UNAUTHORIZED(1010, "未登录或登录已过期"),
    FORBIDDEN(1011, "无权访问"),
    SYSTEM_ERROR(2000, "系统繁忙，请稍后重试"),

    COURSE_NOT_FOUND(2001, "课程不存在"),
    CLASS_NOT_FOUND(2002, "教学班不存在"),
    CAPACITY_NOT_ENOUGH(2003, "容量不足，选课失败"),
    RESERVE_NOT_FOUND(2004, "预占记录不存在"),
    RESERVE_STATE_ILLEGAL(2005, "预占状态不允许该操作"),

    DUPLICATE_ENROLL(3001, "已选过该课程，请勿重复提交"),
    CREDIT_LIMIT_EXCEEDED(3002, "超过本学期学分上限"),
    SCHEDULE_CONFLICT(3003, "与已选课程时间冲突"),
    PREREQUISITE_NOT_MET(3004, "未满足先修课要求"),
    ENROLL_NOT_FOUND(3005, "选课记录不存在");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
