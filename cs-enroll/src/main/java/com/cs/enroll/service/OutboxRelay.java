package com.cs.enroll.service;

import com.cs.common.Result;
import com.cs.enroll.entity.OutboxMessage;
import com.cs.enroll.feign.CourseClient;
import com.cs.enroll.mapper.OutboxMapper;
import com.cs.enroll.mq.EnrollEvent;
import com.cs.enroll.mq.EnrollEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 中继：把 t_outbox_message 里待投递的消息发出去。
 *
 * 两条通道，由 cs.mq.enabled 切换：
 *  - true ：投到 Kafka，由消费者异步处理（生产形态，解耦 + 可多消费方）
 *  - false：直接调下游（没装 Kafka 时的降级通道）
 *
 * 为什么 Outbox 和 MQ 并存，而不是二选一：
 *  - Outbox 解决「业务提交了但消息丢了」——消息与业务同事务落库
 *  - MQ 解决「投递与消费解耦、多消费方、削峰」
 * 两层解决的是不同问题，缺一不可。
 *
 * 重试策略：指数退避（2^retryCount 秒），超过 5 次转死信，人工介入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int MAX_RETRY = 5;

    private final OutboxMapper outboxMapper;
    private final CourseClient courseClient;
    private final EnrollEventProducer producer;

    @Value("${cs.mq.enabled:false}")
    private boolean mqEnabled;

    @Scheduled(fixedDelay = 5_000)
    public void relay() {
        List<OutboxMessage> list = outboxMapper.selectPending(50);
        for (OutboxMessage m : list) {
            try {
                if (mqEnabled) {
                    // 发到 Kafka；send 等到 broker ack 才返回，失败会抛异常走重试
                    producer.send(new EnrollEvent(m.getBizType(), m.getBizKey(), m.getPayload()));
                } else {
                    handleDirectly(m);
                }
                markDone(m);
            } catch (Exception e) {
                retryLater(m, e);
            }
        }
    }

    /** 降级通道：没有 Kafka 时直接调下游 */
    private void handleDirectly(OutboxMessage m) {
        if ("ENROLL_CANCEL".equals(m.getBizType())) {
            Result<Void> r = courseClient.release(m.getPayload());
            // 关键：Feign 拿到 200 不等于成功，必须看业务码
            if (r == null || r.getCode() != 0) {
                throw new IllegalStateException("release 返回失败: " + (r == null ? "null" : r.getMessage()));
            }
        }
        log.info("[Outbox] 直接投递 {} bizKey={}", m.getBizType(), m.getBizKey());
    }

    private void markDone(OutboxMessage m) {
        OutboxMessage upd = new OutboxMessage();
        upd.setId(m.getId());
        upd.setStatus(1);
        outboxMapper.updateById(upd);
    }

    private void retryLater(OutboxMessage m, Exception e) {
        int next = (m.getRetryCount() == null ? 0 : m.getRetryCount()) + 1;
        OutboxMessage upd = new OutboxMessage();
        upd.setId(m.getId());
        upd.setRetryCount(next);
        if (next >= MAX_RETRY) {
            upd.setStatus(2); // 死信
            log.error("[Outbox] 转死信 bizKey={}", m.getBizKey());
        } else {
            upd.setNextRetryTime(LocalDateTime.now().plusSeconds((long) Math.pow(2, next)));
            log.warn("[Outbox] 投递失败，第 {} 次重试 bizKey={} cause={}", next, m.getBizKey(), e.getMessage());
        }
        outboxMapper.updateById(upd);
    }
}
