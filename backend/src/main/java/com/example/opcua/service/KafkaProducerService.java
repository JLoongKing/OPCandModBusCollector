package com.example.opcua.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka生产者服务
 * 实现消息发送和重发机制，确保数据不丢失
 */
@Slf4j
@Service
public class KafkaProducerService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topic.opcua-data}")
    private String opcuaDataTopic;

    @Value("${spring.kafka.producer.max-retry-attempts}")
    private int maxRetryAttempts;

    /**
     * 发送OPC UA数据到Kafka
     * @param message 要发送的消息内容
     */
    @Retryable(
            value = {KafkaException.class, RuntimeException.class},
            maxAttemptsExpression = "${spring.kafka.producer.max-retry-attempts}",
            backoff = @Backoff(delayExpression = "${spring.kafka.producer.retry-delay}", multiplier = 2)
    )
    public void sendOpcuaData(String message) {
        sendMessage(opcuaDataTopic, message);
    }

    /**
     * 发送OPC UA数据到Kafka，带回调函数
     * @param message 要发送的消息内容
     * @return CompletableFuture
     */
    @Retryable(
            value = {KafkaException.class, RuntimeException.class},
            maxAttemptsExpression = "${spring.kafka.producer.max-retry-attempts}",
            backoff = @Backoff(delayExpression = "${spring.kafka.producer.retry-delay}", multiplier = 2)
    )
    public CompletableFuture<SendResult<String, Object>> sendOpcuaDataWithCallback(String message) {
        return sendMessageWithCallback(opcuaDataTopic, message);
    }

    /**
     * 发送消息到指定主题
     * @param topic 主题名称
     * @param message 消息内容
     */
    private void sendMessage(String topic, Object message) {
        try {
            log.debug("准备发送消息到Kafka主题 {}: {}", topic, message);
            ListenableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, message);
            
            future.addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
                @Override
                public void onSuccess(SendResult<String, Object> result) {
                    log.debug("消息发送成功到Kafka主题 {}，偏移量: {}", topic, result.getRecordMetadata().offset());
                }

                @Override
                public void onFailure(Throwable ex) {
                    log.error("消息发送失败到Kafka主题 {}: {}", topic, ex.getMessage());
                    throw new KafkaException("发送消息到Kafka失败", ex);
                }
            });
        } catch (Exception e) {
            log.error("发送消息到Kafka时发生异常: {}", e.getMessage());
            throw new KafkaException("发送消息到Kafka失败", e);
        }
    }

    /**
     * 发送消息到指定主题，返回CompletableFuture
     * @param topic 主题名称
     * @param message 消息内容
     * @return CompletableFuture
     */
    private CompletableFuture<SendResult<String, Object>> sendMessageWithCallback(String topic, Object message) {
        try {
            log.debug("准备发送消息到Kafka主题 {}: {}", topic, message);
            return kafkaTemplate.send(topic, message).completable()
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("消息发送失败到Kafka主题 {}: {}", topic, ex.getMessage());
                        } else {
                            log.debug("消息发送成功到Kafka主题 {}，偏移量: {}", topic, result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("发送消息到Kafka时发生异常: {}", e.getMessage());
            CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
            future.completeExceptionally(new KafkaException("发送消息到Kafka失败", e));
            return future;
        }
    }

    /**
     * 同步发送消息到指定主题
     * @param topic 主题名称
     * @param message 消息内容
     * @return SendResult
     * @throws Exception 发送异常
     */
    @Retryable(
            value = {KafkaException.class, RuntimeException.class},
            maxAttemptsExpression = "${spring.kafka.producer.max-retry-attempts}",
            backoff = @Backoff(delayExpression = "${spring.kafka.producer.retry-delay}", multiplier = 2)
    )
    public SendResult<String, Object> sendMessageSync(String topic, Object message) throws Exception {
        log.debug("同步发送消息到Kafka主题 {}: {}", topic, message);
        return kafkaTemplate.send(topic, message).get();
    }

    /**
     * 批量发送消息
     * @param topic 主题名称
     * @param messages 消息列表
     */
    @Retryable(
            value = {KafkaException.class, RuntimeException.class},
            maxAttemptsExpression = "${spring.kafka.producer.max-retry-attempts}",
            backoff = @Backoff(delayExpression = "${spring.kafka.producer.retry-delay}", multiplier = 2)
    )
    public void sendBatchMessages(String topic, Iterable<Object> messages) {
        log.debug("批量发送消息到Kafka主题 {}，消息数量: {}", topic, messages.spliterator().getExactSizeIfKnown());
        messages.forEach(message -> sendMessage(topic, message));
    }
}