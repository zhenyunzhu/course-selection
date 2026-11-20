package com.cs.common;

import lombok.Getter;

/** 业务异常：Service 层主动抛，GlobalExceptionHandler 统一转成 Result。 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
    }

    public BizException(ResultCode rc, String message) {
        super(message);
        this.code = rc.getCode();
    }

    /** 透传下游返回的码（微服务里经常需要把别的服务的错误码原样上抛） */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
