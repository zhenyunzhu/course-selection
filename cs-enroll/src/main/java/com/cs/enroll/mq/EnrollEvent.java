package com.cs.enroll.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通过 Kafka 传递的领域事件。payload 保持字符串，避免跨服务反序列化耦合。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrollEvent {
    /** ENROLL_SUCCESS / ENROLL_CANCEL */
    private String bizType;
    /** 幂等键：enrollNo */
    private String bizKey;
    /** 业务载荷：取消事件里是 reserveId */
    private String payload;
}
