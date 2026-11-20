package com.cs.enroll.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * 消费失败的处理策略：退避重试 → 转死信。
 *
 * 为什么必须显式配：
 * 默认行为是「立即重试 10 次，失败后跳过并提交位点」——
 * 既会把下游打崩（无间隔重试），又会静默丢消息（跳过 = 永不重投）。
 *
 * 这里的策略：0.5s → 1s → 2s → 4s 共 4 次，仍失败则投递到 cs-enroll-events.DLT。
 */
@Slf4j
@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    log.error("[MQ] 消息进入死信 topic={} key={} cause={}",
                            record.topic() + ".DLT", record.key(), ex.getMessage());
                    return new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", 0);
                });

        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxInterval(4000L);
        backOff.setMaxElapsedTime(4000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // 反序列化错误不该重试（数据本身坏了），直接进死信
        handler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class);
        return handler;
    }
}
