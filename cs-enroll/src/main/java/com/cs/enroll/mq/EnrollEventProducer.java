package com.cs.enroll.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 事件生产者。
 *
 * 关键点：send 之后要 get(timeout) 等 broker 确认，再返回成功。
 * 只是 send 出去不代表投递成功 —— 没等到 ack 就把 Outbox 标成已投递，消息可能永久丢失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${cs.mq.topic:cs-enroll-events}")
    private String topic;

    /**
     * @param key 用业务键做 key，保证同一笔选课的事件进同一分区，消费有序
     */
    public void send(EnrollEvent event) {
        try {
            kafkaTemplate.send(topic, event.getBizKey(), toJson(event))
                    .get(3, TimeUnit.SECONDS);
            log.info("[MQ] 已投递 {} bizKey={}", event.getBizType(), event.getBizKey());
        } catch (Exception e) {
            throw new IllegalStateException("Kafka 投递失败: " + e.getMessage(), e);
        }
    }

    private String toJson(EnrollEvent e) {
        return "{\"bizType\":\"" + e.getBizType() + "\",\"bizKey\":\"" + e.getBizKey()
                + "\",\"payload\":\"" + (e.getPayload() == null ? "" : e.getPayload()) + "\"}";
    }
}
