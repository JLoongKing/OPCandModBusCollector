package com.example.opcua.service;

import com.example.opcua.entity.DataRecord;
import com.example.opcua.entity.Task;
import com.example.opcua.entity.TaskPoint;
import com.example.opcua.repository.TaskRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TaskExecutorService {

    @Autowired
    private DataRecordService dataRecordService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired(required = false)
    private KafkaProducerService kafkaProducerService;

    private final Map<Long, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledExecutorService> taskExecutors = new ConcurrentHashMap<>();
    private final Map<Long, OpcUaClient> opcUaClients = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private final Map<Long, List<DataCacheEntry>> dataCache = new ConcurrentHashMap<>();
    private final Map<Long, BigDecimal> dataCount = new ConcurrentHashMap<>();
    
    /**
     * 数据记录内部类，用于Kafka报文构建
     */
    private static class DataRecord {
        private String pointName;
        private String nodeId;
        private String value;
        private LocalDateTime timestamp;
        
        public DataRecord() {}
        
        public DataRecord(String pointName, String nodeId, String value, LocalDateTime timestamp) {
            this.pointName = pointName;
            this.nodeId = nodeId;
            this.value = value;
            this.timestamp = timestamp;
        }
        
        public String getPointName() {
            return pointName;
        }
        
        public void setPointName(String pointName) {
            this.pointName = pointName;
        }
        
        public String getNodeId() {
            return nodeId;
        }
        
        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }
        
        public String getValue() {
            return value;
        }
        
        public void setValue(String value) {
            this.value = value;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataCacheEntry {
        private Long taskId;
        private String taskName;
        private String pointName;
        private String nodeId;
        private String protocolType;
        private String address;
        private String value;
        private String dataType;
        private String devId;
        private LocalDateTime timestamp;
    }

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
                log.error("任务 {} 数据采集异常 - 错误类型: {}, 错误信息: {}",
                        taskId, e.getClass().getName(), e.getMessage(), e);
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
            log.info("任务 {} 正在断开OPC UA连接...", taskId);
            try {
                client.disconnect().get(5, TimeUnit.SECONDS);
                log.info("任务 {} OPC UA连接已断开", taskId);
            } catch (Exception e) {
                log.warn("任务 {} 断开OPC UA连接异常 - 错误类型: {}, 错误信息: {}",
                        taskId, e.getClass().getName(), e.getMessage());
            }
        }
    }

    /**
     * 数据采集主方法
     * <p>
     * 根据任务协议类型调用不同的采集逻辑，并将采集结果发送到Kafka
     * 
     * @param task 任务对象
     */
    private void collectData(Task task) {
        if (task.getPoints() == null || task.getPoints().isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        // 按设备分组存储数据
        Map<String, List<DataRecord>> deviceDataMap = new HashMap<>();

        if ("OPC_UA".equals(task.getProtocolType())) {
            collectOpcUaWithDeviceGroup(task, now, deviceDataMap);
        } else if ("MODBUS_TCP".equals(task.getProtocolType())) {
            collectModbusWithDeviceGroup(task, now, deviceDataMap);
        } else {
            collectSimulatedWithDeviceGroup(task, now, deviceDataMap);
        }

        if (Boolean.TRUE.equals(task.getKafkaEnabled()) && kafkaProducerService != null && !deviceDataMap.isEmpty()) {
            try {
                String kafkaMessage = buildKafkaMessage(task, deviceDataMap);
                kafkaProducerService.sendWithTaskConfig(task, kafkaMessage);
            } catch (Exception e) {
                log.error("任务 {} Kafka发送失败: {}", task.getId(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * 构建指定格式的Kafka报文
     * <p>
     * 格式说明：
     * 头：HDMY;YZT_XJH;;;;;;;;
     * 节点区：{dev1}~{node1};{val1};{time}||{node2};{val2};{time}||,{dev2}~{node1};{val1};{time}||{node2};{val2};{time}||
     * 
     * @param task 任务对象
     * @param deviceDataMap 按设备分组的数据
     * @return 格式化后的Kafka报文
     */
    private String buildKafkaMessage(Task task, Map<String, List<DataRecord>> deviceDataMap) {
        // 构建头部
        StringBuilder header = new StringBuilder("HDMY;YZT_XJH;;;;;;;;");
        
        // 构建节点区
        StringBuilder nodeSection = new StringBuilder();
        
        // 遍历每个设备的数据
        for (Map.Entry<String, List<DataRecord>> entry : deviceDataMap.entrySet()) {
            String deviceId = entry.getKey();
            List<DataRecord> records = entry.getValue();
            
            if (records.isEmpty()) {
                continue;
            }
            
            // 添加设备分隔符
            if (nodeSection.length() > 0) {
                nodeSection.append(",");
            }
            
            // 添加设备前缀
            nodeSection.append(deviceId).append("~");
            
            // 添加该设备的所有节点数据
            for (int i = 0; i < records.size(); i++) {
                if (i > 0) {
                    nodeSection.append("||");
                }
                
                DataRecord record = records.get(i);
                // 格式：{node1};{val1};{time}
                // 格式化时间为yyyy-MM-dd HH:mm:ss格式
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String timeStr = record.getTimestamp().format(formatter);
                if ("false".equals(record.getValue()) || "true".equals(record.getValue())){
                    record.setValue(record.getValue().equals("true") ? "1" : "0");
                }
                nodeSection.append(record.getNodeId())
                          .append(";").append(record.getValue())
                          .append(";").append(timeStr);
            }
            
            // 每个设备数据结尾添加||
            nodeSection.append("||");
        }
        
        return header.append(",").append(nodeSection).append(",").toString();
    }

    /**
     * OPC UA数据采集（按设备分组）
     * <p>
     * 从OPC UA服务器读取数据，并按设备ID分组存储
     * 优先使用设备产生的时间戳
     * 
     * @param task 任务对象
     * @param now 采集时间
     * @param deviceDataMap 存储结果的设备分组Map
     */
    private void collectOpcUaWithDeviceGroup(Task task, LocalDateTime now, Map<String, List<DataRecord>> deviceDataMap) {
        OpcUaClient client = opcUaClients.get(task.getId());
        if (client == null) {
            log.info("任务 {} 未找到已存在的OPC UA连接，尝试新建连接...", task.getId());
            try {
                client = connectOpcUaClient(task);
                opcUaClients.put(task.getId(), client);
                log.info("任务 {} OPC UA连接已缓存", task.getId());
            } catch (Exception e) {
                log.error("任务 {} OPC UA 连接失败，本周期跳过 - 错误类型: {}, 错误信息: {}",
                        task.getId(), e.getClass().getName(), e.getMessage(), e);
                return;
            }
        }

        List<NodeId> nodeIds = new ArrayList<>();
        List<TaskPoint> orderedPoints = new ArrayList<>();
        String defaultNamespace = task.getOpcNamespace();
        
        // 如果任务没有设置命名空间，默认使用ns=2（常见的OPC UA命名空间）
        if (defaultNamespace == null || defaultNamespace.isEmpty()) {
            defaultNamespace = "2";
            log.info("任务 {} 未设置OPC UA命名空间，默认使用ns=2", task.getId());
        }
        
        for (TaskPoint point : task.getPoints()) {
            try {
                String address = point.getAddress().trim();
                NodeId nodeId;
                String fullAddress;
                
                // 如果地址已经包含命名空间（如ns=2;s=Bu/test），直接使用
                if (address.startsWith("ns=")) {
                    fullAddress = address;
                } else {
                    // 如果地址不包含命名空间，使用任务的默认命名空间
                    // 拼接命名空间和节点ID，格式为ns=2;s=Bu/test
                    fullAddress = String.format("ns=%s;s=%s", defaultNamespace, address);
                }
                
                // 解析为NodeId
                nodeId = NodeId.parse(fullAddress);
                log.debug("任务 {} 点位 {} 完整地址: {}", task.getId(), point.getName(), fullAddress);
                
                nodeIds.add(nodeId);
                orderedPoints.add(point);
            } catch (Exception e) {
                log.warn("任务 {} 点位 {} 地址非法: {}，错误信息: {}", task.getId(), point.getName(), point.getAddress(), e.getMessage());
            }
        }
        if (nodeIds.isEmpty()) {
            log.warn("任务 {} 没有有效节点ID，跳过采集", task.getId());
            return;
        }

        try {
            int timeoutMs = task.getOpcSessionTimeout() != null ? task.getOpcSessionTimeout() : 5000;
            log.info("任务 {} 开始读取 {} 个节点, 超时: {}ms", task.getId(), nodeIds.size(), timeoutMs);
            List<DataValue> values = client.readValues(0.0, TimestampsToReturn.Both, nodeIds)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            log.info("任务 {} 读取完成，获取 {} 个值", task.getId(), values != null ? values.size() : 0);

            for (int i = 0; i < orderedPoints.size(); i++) {
                TaskPoint point = orderedPoints.get(i);
                DataValue value = i < values.size() ? values.get(i) : null;
                String valueStr = valueToString(value);
                
                // 优先使用设备产生的时间（源时间戳）
                LocalDateTime hardwareTime = now;
                if (value != null) {
                    // 先尝试获取源时间戳（设备产生的时间）
                    if (value.getSourceTime() != null) {
                        try {
                            hardwareTime = value.getSourceTime().getJavaDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                            log.debug("使用设备时间戳: {} 点位: {}", hardwareTime, point.getName());
                        } catch (Exception e) {
                            log.warn("解析源时间戳失败: {}", e.getMessage());
                        }
                    } 
                    // 再尝试获取服务器时间戳
                    else if (value.getServerTime() != null) {
                        try {
                            hardwareTime = value.getServerTime().getJavaDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                            log.debug("使用服务器时间戳: {} 点位: {}", hardwareTime, point.getName());
                        } catch (Exception e) {
                            log.warn("解析服务器时间戳失败: {}", e.getMessage());
                        }
                    }
                    // 最后才使用本地系统时间
                    else {
                        log.debug("使用本地系统时间: {} 点位: {}", hardwareTime, point.getName());
                    }
                }
                
                // 持久化记录
                if (valueStr != null && !valueStr.isEmpty()) {
                    DataRecord record = persistRecord(task, point, valueStr, hardwareTime);
                    
                    // 按设备分组
                    String deviceId = point.getDevId() != null && !point.getDevId().trim().isEmpty()
                            ? point.getDevId().trim()
                            : "DEFAULT_DEV";
                    
                    deviceDataMap.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(record);
                } else {
                    log.warn("任务 {} 点位 {} 读取到空值，跳过存储", task.getId(), point.getName());
                }
            }
        } catch (Exception e) {
            log.error("任务 {} OPC UA 读取失败，将尝试重连 - 错误类型: {}, 错误信息: {}",
                    task.getId(), e.getClass().getName(), e.getMessage(), e);
            disconnectOpcUa(task.getId());
        }
    }
    
    // 保留原方法用于兼容
    private void collectOpcUa(Task task, LocalDateTime now, List<String> kafkaParts) {
        Map<String, List<DataRecord>> deviceDataMap = new HashMap<>();
        collectOpcUaWithDeviceGroup(task, now, deviceDataMap);
        
        // 转换为原格式
        for (List<DataRecord> records : deviceDataMap.values()) {
            for (DataRecord record : records) {
                kafkaParts.add(record.getPointName() + "=" + record.getValue());
            }
        }
    }

    private OpcUaClient connectOpcUaClient(Task task) throws Exception {
        String endpointUrl = task.getOpcServerUrl();
        log.info("任务 {} 开始连接OPC UA服务器: {}", task.getId(), endpointUrl);
        log.info("任务 {} 连接参数 - 安全策略: {}, 安全模式: {}, 用户名: {}, 超时: {}ms",
                task.getId(),
                task.getOpcSecurityPolicy() != null ? task.getOpcSecurityPolicy() : "None",
                task.getOpcSecurityMode() != null ? task.getOpcSecurityMode() : "None",
                task.getOpcUsername() != null && !task.getOpcUsername().isEmpty() ? "已设置" : "匿名",
                task.getOpcSessionTimeout() != null ? task.getOpcSessionTimeout() : 5000);

        if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
            log.error("任务 {} OPC UA 服务器地址为空", task.getId());
            throw new IllegalArgumentException("OPC UA 服务器地址为空");
        }
        endpointUrl = endpointUrl.trim();

        log.info("任务 {} 开始发现Endpoints: {}", task.getId(), endpointUrl);
        List<EndpointDescription> endpoints;
        try {
            endpoints = DiscoveryClient.getEndpoints(endpointUrl).get();
            log.info("任务 {} 发现 {} 个Endpoints", task.getId(), endpoints != null ? endpoints.size() : 0);
            if (endpoints != null) {
                for (int i = 0; i < endpoints.size(); i++) {
                    EndpointDescription ep = endpoints.get(i);
                    log.info("任务 {} Endpoint[{}] - URL: {}, 安全策略: {}, 安全模式: {}",
                            task.getId(), i, ep.getEndpointUrl(), ep.getSecurityPolicyUri(), ep.getSecurityMode());
                }
            }
        } catch (Exception e) {
            log.error("任务 {} 发现Endpoints失败: {} - 完整URL: {}",
                    task.getId(), e.getMessage(), endpointUrl, e);
            throw e;
        }

        if (endpoints == null || endpoints.isEmpty()) {
            log.error("任务 {} 未发现可用Endpoints, URL: {}", task.getId(), endpointUrl);
            throw new IllegalStateException("未发现可用 Endpoints");
        }

        EndpointDescription endpoint = selectEndpoint(endpoints, task);
        log.info("任务 {} 选择Endpoint - URL: {}, 安全策略: {}, 安全模式: {}",
                task.getId(), endpoint.getEndpointUrl(), endpoint.getSecurityPolicyUri(), endpoint.getSecurityMode());

        IdentityProvider identity = buildIdentity(task);
        log.info("任务 {} 认证方式: {}", task.getId(),
                identity instanceof UsernameProvider ? "用户名密码认证" : "匿名认证");

        int timeoutMs = task.getOpcSessionTimeout() != null ? task.getOpcSessionTimeout() : 5000;
        log.info("任务 {} 创建OPC UA客户端配置, 请求超时: {}ms", task.getId(), timeoutMs);

        OpcUaClientConfig config = OpcUaClientConfig.builder()
                .setApplicationName(LocalizedText.english("opcua-task-" + task.getId()))
                .setApplicationUri("urn:opcua:task:" + task.getId())
                .setEndpoint(endpoint)
                .setIdentityProvider(identity)
                .setRequestTimeout(UInteger.valueOf(timeoutMs))
                .build();

        OpcUaClient client = OpcUaClient.create(config);
        log.info("任务 {} 开始建立OPC UA连接...", task.getId());
        try {
            client.connect().get(timeoutMs, TimeUnit.MILLISECONDS);
            log.info("任务 {} OPC UA连接成功 - URL: {}", task.getId(), endpoint.getEndpointUrl());
        } catch (Exception e) {
            log.error("任务 {} OPC UA连接失败 - URL: {}, 错误类型: {}, 错误信息: {}",
                    task.getId(), endpoint.getEndpointUrl(), e.getClass().getName(), e.getMessage(), e);
            throw e;
        }
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
            return "null";
        }
        org.eclipse.milo.opcua.stack.core.types.builtin.Variant variant = dataValue.getValue();
        if (variant == null || variant.isNull()) {
            return "null";
        }
        Object v = variant.getValue();
        if (v == null) {
            return "null";
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

    /**
     * Modbus TCP数据采集（按设备分组）
     * <p>
     * 从Modbus TCP服务器读取数据，并按设备ID分组存储
     * 使用数据读取完成的时间作为时间戳
     * 
     * @param task 任务对象
     * @param now 采集时间
     * @param deviceDataMap 存储结果的设备分组Map
     */
    private void collectModbusWithDeviceGroup(Task task, LocalDateTime now, Map<String, List<DataRecord>> deviceDataMap) {
        String host = task.getModbusHost();
        int port = task.getModbusPort() != null ? task.getModbusPort() : 502;
        int timeout = task.getModbusTimeout() != null ? task.getModbusTimeout() : 3000;

        for (TaskPoint point : task.getPoints()) {
            String valueStr;
            LocalDateTime dataTime = now; // 默认使用当前时间
            
            try {
                String result = ModbusTcpUtil.readPoint(host, port, timeout, point.getAddress(), 
                        point.getDevId(), point.getNodeId(), point.getDataType(), 
                        point.getBitLength(), point.getScaleFactor());
                
                // 解析结果和时间戳（格式: value|timestamp）
                if (result.contains("|")) {
                    String[] parts = result.split("\\|", 2);
                    valueStr = parts[0];
                    try {
                        // 解析时间戳
                        dataTime = LocalDateTime.parse(parts[1], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        log.debug("使用Modbus数据获取时间: {} 点位: {}", dataTime, point.getName());
                    } catch (Exception e) {
                        log.warn("解析Modbus时间戳失败: {}", e.getMessage());
                    }
                } else {
                    valueStr = result;
                }
            } catch (Exception e) {
                log.warn("任务 {} Modbus 读点位 {} 失败 (host={}:{}) - 错误: {}", task.getId(), point.getName(), host, port, e.getMessage());
                valueStr = "ERROR:" + e.getMessage();
            }
            
            // 持久化记录
            DataRecord record = persistRecord(task, point, valueStr, dataTime);
            
            // 按设备分组
            String deviceId = point.getDevId() != null && !point.getDevId().trim().isEmpty()
                    ? point.getDevId().trim()
                    : "DEFAULT_DEV";
            
            deviceDataMap.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(record);
        }
    }
    
    // 保留原方法用于兼容
    private void collectModbus(Task task, LocalDateTime now, List<String> kafkaParts) {
        Map<String, List<DataRecord>> deviceDataMap = new HashMap<>();
        collectModbusWithDeviceGroup(task, now, deviceDataMap);
        
        // 转换为原格式
        for (List<DataRecord> records : deviceDataMap.values()) {
            for (DataRecord record : records) {
                kafkaParts.add(record.getPointName() + "=" + record.getValue());
            }
        }
    }

    /**
     * 模拟数据采集（按设备分组）
     * <p>
     * 生成模拟数据，并按设备ID分组存储
     * 使用采集时间作为时间戳
     * 
     * @param task 任务对象
     * @param now 采集时间
     * @param deviceDataMap 存储结果的设备分组Map
     */
    private void collectSimulatedWithDeviceGroup(Task task, LocalDateTime now, Map<String, List<DataRecord>> deviceDataMap) {
        for (TaskPoint point : task.getPoints()) {
            String valueStr = generateSimulatedValue(point);
            
            // 模拟数据使用当前时间作为数据产生时间
            LocalDateTime simulatedTime = now;
            
            // 持久化记录
            DataRecord record = persistRecord(task, point, valueStr, simulatedTime);
            
            // 按设备分组
            String deviceId = point.getDevId() != null && !point.getDevId().trim().isEmpty()
                    ? point.getDevId().trim()
                    : "DEFAULT_DEV";
            
            deviceDataMap.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(record);
        }
    }
    
    // 保留原方法用于兼容
    private void collectSimulated(Task task, LocalDateTime now, List<String> kafkaParts) {
        Map<String, List<DataRecord>> deviceDataMap = new HashMap<>();
        collectSimulatedWithDeviceGroup(task, now, deviceDataMap);
        
        // 转换为原格式
        for (List<DataRecord> records : deviceDataMap.values()) {
            for (DataRecord record : records) {
                kafkaParts.add(record.getPointName() + "=" + record.getValue());
            }
        }
    }

    private DataRecord persistRecord(Task task, TaskPoint point, String valueStr, LocalDateTime now) {
        // 保存到数据库（暂时注释）
        // com.example.opcua.entity.DataRecord dbRecord = new com.example.opcua.entity.DataRecord();
        // dbRecord.setTaskId(task.getId());
        // dbRecord.setTaskName(task.getName());
        // dbRecord.setPointName(point.getName());
        // dbRecord.setProtocolType(task.getProtocolType());
        // dbRecord.setAddress(point.getAddress());
        // dbRecord.setValue(valueStr);
        // dbRecord.setTimestamp(now);
        // String dataType = autoDetectDataType(valueStr);
        // dbRecord.setDataType(dataType);
        // dataRecordService.save(dbRecord);
        // log.info("任务 {} 点位 {} 数据已存储到分表: {}, 值: {}", task.getId(), point.getName(), getTableSuffix(now), valueStr);

        // 存入内存缓存
        DataCacheEntry cacheEntry = new DataCacheEntry();
        cacheEntry.setTaskId(task.getId());
        cacheEntry.setTaskName(task.getName());
        cacheEntry.setPointName(point.getName());
        cacheEntry.setNodeId(point.getNodeId());
        cacheEntry.setProtocolType(task.getProtocolType());
        cacheEntry.setAddress(point.getAddress());
        cacheEntry.setValue(valueStr);
        cacheEntry.setDataType(autoDetectDataType(valueStr));
        cacheEntry.setTimestamp(now);
        cacheEntry.setDevId(point.getDevId());
        addToCache(cacheEntry);

        // 创建内部DataRecord对象用于Kafka
        DataRecord kafkaRecord = new DataRecord();
        kafkaRecord.setPointName(point.getName());
        kafkaRecord.setNodeId(point.getNodeId());
        kafkaRecord.setValue(valueStr);
        kafkaRecord.setTimestamp(now);

        return kafkaRecord;
    }

    /**
     * 添加数据到内存缓存，每个任务最多缓存10000条
     * 同时用BigDecimal一直累加数据总量
     */
    private void addToCache(DataCacheEntry entry) {
        List<DataCacheEntry> list = dataCache.computeIfAbsent(entry.getTaskId(), k -> new ArrayList<>());
        synchronized (list) {
            list.add(entry);
            if (list.size() > 10000) {
                list.subList(0, list.size() - 10000).clear();
            }
        }
        
        dataCount.compute(entry.getTaskId(), (k, v) -> (v == null) ? BigDecimal.ONE : v.add(BigDecimal.ONE));
    }

    /**
     * 获取任务缓存数据
     */
    public List<DataCacheEntry> getCacheData(Long taskId, Long seconds) {
        List<DataCacheEntry> all = dataCache.getOrDefault(taskId, new ArrayList<>());
        if (all.isEmpty()) {
            return new ArrayList<>();
        }
        if (seconds == null || seconds <= 0) {
            return new ArrayList<>(all);
        }
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(seconds);
        List<DataCacheEntry> recent = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            DataCacheEntry entry = all.get(i);
            if (entry.getTimestamp() != null && entry.getTimestamp().isAfter(threshold)) {
                recent.add(entry);
            }
        }
        return recent;
    }

    /**
     * 根据点位名称获取缓存数据
     */
    public List<DataCacheEntry> getCacheDataByPointName(String pointName, Long seconds) {
        List<DataCacheEntry> result = new ArrayList<>();
        for (List<DataCacheEntry> entries : dataCache.values()) {
            for (int i = entries.size() - 1; i >= 0; i--) {
                DataCacheEntry entry = entries.get(i);
                if (pointName.equals(entry.getPointName())) {
                    if (seconds != null && seconds > 0) {
                        LocalDateTime threshold = LocalDateTime.now().minusSeconds(seconds);
                        if (entry.getTimestamp() == null || !entry.getTimestamp().isAfter(threshold)) {
                            continue;
                        }
                    }
                    result.add(entry);
                }
            }
        }
        return result;
    }
    
    /**
     * 获取任务的数据总量（一直累加，不受缓存10000条限制）
     */
    public BigDecimal getDataCount(Long taskId) {
        return dataCount.getOrDefault(taskId, BigDecimal.ZERO);
    }

    private static String getTableSuffix(LocalDateTime timestamp) {
        return timestamp.format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private String autoDetectDataType(String value) {
        if (value == null || value.equalsIgnoreCase("null")) {
            return "String";
        }
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
