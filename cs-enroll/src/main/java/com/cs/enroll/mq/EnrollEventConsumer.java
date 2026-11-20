package com.cs.enroll.mq;

import com.cs.common.Result;
import com.cs.enroll.feign.CourseClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 事件消费者。
 *
 * 三件事必须做对：
 *  1. 手动 ACK —— 处理失败不提交位点，让 Kafka 重投
 *  2. 幂等 —— 重投时不能重复释放容量 / 重复发通知（用 Redis SETNX 去重）
 *  3. 不吞异常 —— 抛出去才会重试，吞了就丢消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cs.mq.enabled", havingValue = "true", matchIfMissing = true)
public class EnrollEventConsumer {

    private final CourseClient courseClient;
    private final StringRedisTemplate redis;

    @KafkaListener(topics = "${cs.mq.topic:cs-enroll-events}", groupId = "cs-enroll-worker")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String body = record.value();
        EnrollEvent event = parse(body);

        // 幂等：同一个 bizKey + bizType 只处理一次（Kafka 是 at-least-once）
        String dedupeKey = "mq:done:" + event.getBizType() + ":" + event.getBizKey();
        Boolean first = redis.opsForValue().setIfAbsent(dedupeKey, "1", Duration.ofHours(24));
        if (!Boolean.TRUE.equals(first)) {
            log.info("[MQ] 重复消息，跳过 {} bizKey={}", event.getBizType(), event.getBizKey());
            ack.acknowledge();
            return;
        }

        try {
            if ("ENROLL_CANCEL".equals(event.getBizType())) {
                Result<Void> r = courseClient.release(event.getPayload());
                if (r == null || r.getCode() != 0) {
                    throw new IllegalStateException("释放容量失败: " + (r == null ? "null" : r.getMessage()));
                }
                log.info("[MQ] 退课释放容量成功 reserveId={}", event.getPayload());
            } else {
                log.info("[MQ] 发送选课通知 enrollNo={}（模拟短信/站内信）", event.getBizKey());
            }
            ack.acknowledge();
        } catch (Exception e) {
            // 删掉幂等标记，否则重投会被自己挡住
            redis.delete(dedupeKey);
            log.error("[MQ] 处理失败，等待重投 bizKey={}", event.getBizKey(), e);
            throw e;
        }
    }

    private EnrollEvent parse(String body) {
        EnrollEvent e = new EnrollEvent();
        e.setBizType(extract(body, "bizType"));
        e.setBizKey(extract(body, "bizKey"));
        e.setPayload(extract(body, "payload"));
        return e;
    }

    private String extract(String json, String field) {
        String key = "\"" + field + "\":\"";
        int i = json.indexOf(key);
        if (i < 0) {
            return "";
        }
        int start = i + key.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }
}
