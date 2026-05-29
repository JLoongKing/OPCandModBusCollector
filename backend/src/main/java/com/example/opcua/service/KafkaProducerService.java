package com.example.opcua.service;

import com.example.opcua.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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

    @Value("${spring.kafka.debug-mode:false}")
    private boolean debugMode;

    private final Map<String, KafkaTemplate<String, Object>> templateCache = new ConcurrentHashMap<>();

    private KafkaTemplate<String, Object> getTemplateForTask(Task task) {
        String bootstrapServers = task.getKafkaBootstrapServers();

        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            log.debug("任务 {} 未配置Kafka地址，使用全局KafkaTemplate", task.getId());
            return kafkaTemplate;
        }

        return templateCache.computeIfAbsent(bootstrapServers, bs -> {
            log.info("为Kafka地址 {} 创建新的Producer", bs);
            Map<String, Object> configProps = new HashMap<>();
            configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bs);
            configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

            String acks = task.getKafkaAcks() != null ? task.getKafkaAcks() : "1";
            configProps.put(ProducerConfig.ACKS_CONFIG, acks);

            int retries = task.getKafkaRetries() != null ? task.getKafkaRetries() : 3;
            configProps.put(ProducerConfig.RETRIES_CONFIG, retries);

            int batchSize = task.getKafkaBatchSize() != null ? task.getKafkaBatchSize() : 16384;
            configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);

            int lingerMs = task.getKafkaLingerMs() != null ? task.getKafkaLingerMs() : 0;
            configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);

            long bufferMemory = task.getKafkaBufferMemory() != null ? task.getKafkaBufferMemory() : 33554432L;
            configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);

            DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(configProps);
            return new KafkaTemplate<>(factory);
        });
    }

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

    private String truncateMsg(Object msg) {
        if (msg == null) {
            return "null";
        }
        String s = msg.toString();
        if (debugMode || s.length() <= 100) {
            return s;
        }
        return s.substring(0, 100) + "...";
    }

    /**
     * 发送消息到指定主题
     * @param topic 主题名称
     * @param message 消息内容
     */
    private void sendMessage(String topic, Object message) {
        try {
            log.debug("准备发送消息到Kafka主题 {}: {}", topic, truncateMsg(message));
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
            log.debug("准备发送消息到Kafka主题 {}: {}", topic, truncateMsg(message));
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
        log.debug("同步发送消息到Kafka主题 {}: {}", topic, truncateMsg(message));
        return kafkaTemplate.send(topic, message).get();
    }

    /**
     * 同步发送消息到指定主题，带Key
     * @param topic 主题名称
     * @param key 消息Key
     * @param message 消息内容
     * @return SendResult
     * @throws Exception 发送异常
     */
    @Retryable(
            value = {KafkaException.class, RuntimeException.class},
            maxAttemptsExpression = "${spring.kafka.producer.max-retry-attempts}",
            backoff = @Backoff(delayExpression = "${spring.kafka.producer.retry-delay}", multiplier = 2)
    )
    public SendResult<String, Object> sendMessageSync(String topic, String key, Object message) throws Exception {
        log.debug("同步发送消息到Kafka主题 {} (key={}): {}", topic, key, truncateMsg(message));
        return kafkaTemplate.send(topic, key, message).get();
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

    /**
     * 使用任务级Kafka配置发送消息
     * 优先使用任务配置的kafkaBootstrapServers，未配置则使用全局配置
     * @param task 任务对象（含Kafka配置）
     * @param message 消息内容
     * @throws Exception 发送异常
     */
    public void sendWithTaskConfig(Task task, String message) throws Exception {
        String topic = task.getKafkaTopic() != null && !task.getKafkaTopic().isEmpty()
                ? task.getKafkaTopic()
                : opcuaDataTopic;

        String key = task.getKafkaKey() != null && !task.getKafkaKey().isEmpty()
                ? task.getKafkaKey()
                : null;

        if (debugMode) {
            if (key != null) {
                log.info("任务 {} [DEBUG] Kafka不发送 (topic={}, key={}, 消息长度={}): {}",
                        task.getId(), topic, key, message.length(), truncateMsg(message));
            } else {
                log.info("任务 {} [DEBUG] Kafka不发送 (topic={}, 消息长度={}): {}",
                        task.getId(), topic, message.length(), truncateMsg(message));
            }
            return;
        }

        KafkaTemplate<String, Object> template = getTemplateForTask(task);

        if (key != null) {
            template.send(topic, key, message).get();
            log.info("任务 {} Kafka发送成功 (topic={}, key={}, 消息长度={}): {}",
                    task.getId(), topic, key, message.length(), truncateMsg(message));
        } else {
            template.send(topic, message).get();
            log.info("任务 {} Kafka发送成功 (topic={}, 消息长度={}): {}",
                    task.getId(), topic, message.length(), truncateMsg(message));
        }
    }
}