package com.cs.enroll.context;

/** 当前学生身份（从网关注入的请求头取，或从 token 解析）。用完必须 clear。 */
public final class StudentContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private StudentContext() {
    }

    public static void set(Long studentId) {
        HOLDER.set(studentId);
    }

    public static Long get() {
        return HOLDER.get();
    }

    public static Long require() {
        Long id = HOLDER.get();
        if (id == null) {
            throw new com.cs.common.BizException(com.cs.common.ResultCode.UNAUTHORIZED);
        }
        return id;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
