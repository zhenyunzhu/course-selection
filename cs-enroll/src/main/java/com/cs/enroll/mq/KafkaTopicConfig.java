package com.cs.enroll.mq;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** 启动时确保 topic 存在（分区 3，副本 1：单机开发环境）。 */
@Configuration
@ConditionalOnProperty(name = "cs.mq.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Value("${cs.mq.topic:cs-enroll-events}")
    private String topic;

    @Bean
    public NewTopic enrollEventsTopic() {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
