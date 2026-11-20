package com.cs.enroll.service;

import com.cs.common.Result;
import com.cs.enroll.entity.OutboxMessage;
import com.cs.enroll.feign.CourseClient;
import com.cs.enroll.mapper.OutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 中继：把 t_outbox_message 里待投递的消息发出去。
 *
 * 两种消息：
 *  - ENROLL_SUCCESS：通知学生（这里只打日志，生产接短信/推送）
 *  - ENROLL_CANCEL ：补偿 —— 调用 course 服务释放容量
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
    private final EnrollService enrollService;

    @Scheduled(fixedDelay = 5_000)
    public void relay() {
        List<OutboxMessage> list = outboxMapper.selectPending(50);
        for (OutboxMessage m : list) {
            try {
                if ("ENROLL_CANCEL".equals(m.getBizType())) {
                    Result<Void> r = courseClient.release(m.getPayload());
                    // 关键：Feign 拿到 200 不等于成功，必须看业务码
                    if (r == null || r.getCode() != 0) {
                        throw new IllegalStateException("release 返回失败: " + (r == null ? "null" : r.getMessage()));
                    }
                }
                // ENROLL_SUCCESS 这里模拟"发送通知"
                log.info("[Outbox] 投递 {} bizKey={} payload={}", m.getBizType(), m.getBizKey(), m.getPayload());
                markDone(m);
            } catch (Exception e) {
                retryLater(m, e);
            }
        }
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

