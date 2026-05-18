package com.example.opcua.service;

import com.example.opcua.entity.DataRecord;
import com.example.opcua.entity.Task;
import com.example.opcua.entity.TaskPoint;
import com.example.opcua.repository.DataRecordRepository;
import com.example.opcua.repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TaskExecutorService {

    @Autowired
    private DataRecordRepository dataRecordRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired(required = false)
    private KafkaProducerService kafkaProducerService;

    private final Map<Long, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledExecutorService> taskExecutors = new ConcurrentHashMap<>();
    private final Map<Long, OpcUaClient> opcUaClients = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public void startTask(Task task) {
        Long taskId = task.getId();
        if (runningTasks.containsKey(taskId)) {
            log.warn("任务 {} 已在运行中", taskId);
            return;
        }

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "task-exec-" + taskId);
            t.setDaemon(true);
            return t;
        });
        taskExecutors.put(taskId, executor);

        int scanInterval = 1000;
        if ("MODBUS_TCP".equals(task.getProtocolType()) && task.getModbusScanInterval() != null) {
            scanInterval = Math.max(100, task.getModbusScanInterval());
        } else if ("OPC_UA".equals(task.getProtocolType()) && task.getOpcScanInterval() != null) {
            scanInterval = Math.max(100, task.getOpcScanInterval());
        }

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            try {
                Task latest = taskRepository.findById(taskId).orElse(null);
                if (latest == null) {
                    return;
                }
                collectData(latest);
            } catch (Exception e) {
                log.error("任务 {} 数据采集异常: {}", taskId, e.getMessage());
            }
        }, 0, scanInterval, TimeUnit.MILLISECONDS);

        runningTasks.put(taskId, future);
        log.info("任务 {} ({}) 已启动，协议: {}, 扫描间隔: {}ms", taskId, task.getName(), task.getProtocolType(), scanInterval);
    }

    public void stopTask(Long taskId) {
        ScheduledFuture<?> future = runningTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }

        ScheduledExecutorService executor = taskExecutors.remove(taskId);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        disconnectOpcUa(taskId);
        log.info("任务 {} 已停止", taskId);
    }

    public boolean isTaskRunning(Long taskId) {
        return runningTasks.containsKey(taskId);
    }

    private void disconnectOpcUa(Long taskId) {
        OpcUaClient client = opcUaClients.remove(taskId);
        if (client != null) {
            try {
                client.disconnect().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("断开 OPC UA 客户端: {}", e.getMessage());
            }
        }
    }

    private void collectData(Task task) {
        if (task.getPoints() == null || task.getPoints().isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<String> kafkaParts = new ArrayList<>();

        if ("OPC_UA".equals(task.getProtocolType())) {
            collectOpcUa(task, now, kafkaParts);
        } else if ("MODBUS_TCP".equals(task.getProtocolType())) {
            collectModbus(task, now, kafkaParts);
        } else {
            collectSimulated(task, now, kafkaParts);
        }

        if (Boolean.TRUE.equals(task.getKafkaEnabled()) && kafkaProducerService != null && !kafkaParts.isEmpty()) {
            try {
                String topic = task.getKafkaTopic() != null && !task.getKafkaTopic().isEmpty()
                        ? task.getKafkaTopic()
                        : "data-collection";
                String message = "TASK:" + task.getName() + "|" + String.join(";", kafkaParts) + ";";
                kafkaProducerService.sendMessageSync(topic, message);
            } catch (Exception e) {
                log.error("任务 {} Kafka发送失败: {}", task.getId(), e.getMessage());
            }
        }
    }

    private void collectOpcUa(Task task, LocalDateTime now, List<String> kafkaParts) {
        OpcUaClient client = opcUaClients.get(task.getId());
        if (client == null) {
            try {
                client = connectOpcUaClient(task);
                opcUaClients.put(task.getId(), client);
            } catch (Exception e) {
                log.warn("任务 {} OPC UA 连接失败，本周期跳过: {}", task.getId(), e.getMessage());
                return;
            }
        }

        List<NodeId> nodeIds = new ArrayList<>();
        List<TaskPoint> orderedPoints = new ArrayList<>();
        for (TaskPoint point : task.getPoints()) {
            try {
                nodeIds.add(NodeId.parse(point.getAddress().trim()));
                orderedPoints.add(point);
            } catch (Exception e) {
                log.warn("任务 {} 点位 {} 地址非法: {}", task.getId(), point.getName(), point.getAddress());
            }
        }
        if (nodeIds.isEmpty()) {
            return;
        }

        try {
            int timeoutMs = task.getOpcSessionTimeout() != null ? task.getOpcSessionTimeout() : 5000;
            List<DataValue> values = client.readValues(0.0, TimestampsToReturn.Both, nodeIds)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);

            for (int i = 0; i < orderedPoints.size(); i++) {
                TaskPoint point = orderedPoints.get(i);
                String valueStr = valueToString(i < values.size() ? values.get(i) : null);
                persistRecord(task, point, valueStr, now);
                kafkaParts.add(point.getName() + "=" + valueStr);
            }
        } catch (Exception e) {
            log.warn("任务 {} OPC UA 读取失败，将尝试重连: {}", task.getId(), e.getMessage());
            disconnectOpcUa(task.getId());
        }
    }

    private OpcUaClient connectOpcUaClient(Task task) throws Exception {
        String endpointUrl = task.getOpcServerUrl();
        if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("OPC UA 服务器地址为空");
        }
        endpointUrl = endpointUrl.trim();

        List<EndpointDescription> endpoints = DiscoveryClient.getEndpoints(endpointUrl).get();
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalStateException("未发现可用 Endpoints");
        }

        EndpointDescription endpoint = selectEndpoint(endpoints, task);
        IdentityProvider identity = buildIdentity(task);

        int timeoutMs = task.getOpcSessionTimeout() != null ? task.getOpcSessionTimeout() : 5000;
        OpcUaClientConfig config = OpcUaClientConfig.builder()
                .setApplicationName(LocalizedText.english("opcua-task-" + task.getId()))
                .setApplicationUri("urn:opcua:task:" + task.getId())
                .setEndpoint(endpoint)
                .setIdentityProvider(identity)
                .setRequestTimeout(UInteger.valueOf(timeoutMs))
                .build();

        OpcUaClient client = OpcUaClient.create(config);
        client.connect().get(timeoutMs, TimeUnit.MILLISECONDS);
        log.info("任务 {} 已连接 OPC UA: {}", task.getId(), endpoint.getEndpointUrl());
        return client;
    }

    private static IdentityProvider buildIdentity(Task task) {
        String user = task.getOpcUsername();
        if (user != null && !user.trim().isEmpty()) {
            String pass = task.getOpcPassword() != null ? task.getOpcPassword() : "";
            return new UsernameProvider(user.trim(), pass);
        }
        return new AnonymousProvider();
    }

    private static EndpointDescription selectEndpoint(List<EndpointDescription> endpoints, Task task) {
        String policyName = task.getOpcSecurityPolicy() != null ? task.getOpcSecurityPolicy() : "None";
        SecurityPolicy wanted = securityPolicyFromName(policyName);
        MessageSecurityMode wantedMode = securityModeFromName(task.getOpcSecurityMode());

        return endpoints.stream()
                .filter(e -> wanted.getUri().equals(e.getSecurityPolicyUri()))
                .filter(e -> e.getSecurityMode() == wantedMode)
                .findFirst()
                .orElseGet(() -> endpoints.stream()
                        .filter(e -> SecurityPolicy.None.getUri().equals(e.getSecurityPolicyUri()))
                        .findFirst()
                        .orElse(endpoints.get(0)));
    }

    private static SecurityPolicy securityPolicyFromName(String name) {
        if (name == null) {
            return SecurityPolicy.None;
        }
        switch (name) {
            case "Basic128Rsa15":
                return SecurityPolicy.Basic128Rsa15;
            case "Basic256":
                return SecurityPolicy.Basic256;
            case "Basic256Sha256":
                return SecurityPolicy.Basic256Sha256;
            default:
                return SecurityPolicy.None;
        }
    }

    private static MessageSecurityMode securityModeFromName(String mode) {
        if (mode == null) {
            return MessageSecurityMode.None;
        }
        switch (mode) {
            case "Sign":
                return MessageSecurityMode.Sign;
            case "SignAndEncrypt":
                return MessageSecurityMode.SignAndEncrypt;
            default:
                return MessageSecurityMode.None;
        }
    }

    private static String valueToString(DataValue dataValue) {
        if (dataValue == null || dataValue.getValue() == null) {
            return "";
        }
        org.eclipse.milo.opcua.stack.core.types.builtin.Variant variant = dataValue.getValue();
        if (variant == null || variant.isNull()) {
            return "";
        }
        Object v = variant.getValue();
        if (v == null) {
            return "";
        }
        if (v.getClass().isArray()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int len = java.lang.reflect.Array.getLength(v);
            for (int i = 0; i < len; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                Object el = java.lang.reflect.Array.get(v, i);
                sb.append(el != null ? el.toString() : "null");
            }
            sb.append("]");
            return sb.toString();
        }
        return String.valueOf(v);
    }

    private void collectModbus(Task task, LocalDateTime now, List<String> kafkaParts) {
        String host = task.getModbusHost();
        int port = task.getModbusPort() != null ? task.getModbusPort() : 502;
        int timeout = task.getModbusTimeout() != null ? task.getModbusTimeout() : 3000;

        for (TaskPoint point : task.getPoints()) {
            String valueStr;
            try {
                valueStr = ModbusTcpUtil.readPoint(host, port, timeout, point.getAddress(), point.getDevId(), point.getNodeId(), point.getScaleFactor());
            } catch (Exception e) {
                log.warn("任务 {} Modbus 读点位 {} 失败: {}", task.getId(), point.getName(), e.getMessage());
                valueStr = "ERROR:" + e.getMessage();
            }
            persistRecord(task, point, valueStr, now);
            kafkaParts.add(point.getName() + "=" + valueStr);
        }
    }

    private void collectSimulated(Task task, LocalDateTime now, List<String> kafkaParts) {
        for (TaskPoint point : task.getPoints()) {
            String valueStr = generateSimulatedValue(point);
            persistRecord(task, point, valueStr, now);
            kafkaParts.add(point.getName() + "=" + valueStr);
        }
    }

    private void persistRecord(Task task, TaskPoint point, String valueStr, LocalDateTime now) {
        DataRecord record = new DataRecord();
        record.setTaskId(task.getId());
        record.setTaskName(task.getName());
        record.setPointName(point.getName());
        record.setProtocolType(task.getProtocolType());
        record.setAddress(point.getAddress());
        record.setValue(valueStr);
        record.setTimestamp(now);
        
        // 自动判断数据类型
        String dataType = autoDetectDataType(valueStr);
        record.setDataType(dataType);
        dataRecordRepository.save(record);
    }

    private String autoDetectDataType(String value) {
        try {
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                return "Boolean";
            }
            Integer.parseInt(value);
            return "Integer";
        } catch (NumberFormatException e) {
            try {
                Double.parseDouble(value);
                return "Double";
            } catch (NumberFormatException ex) {
                return "String";
            }
        }
    }

    private String generateSimulatedValue(TaskPoint point) {
        // 默认生成Double类型数据
        return String.format("%.2f", 20 + random.nextDouble() * 50);
    }
}
