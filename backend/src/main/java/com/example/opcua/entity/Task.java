package com.example.opcua.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "task")
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "protocol_type", length = 20, nullable = false)
    private String protocolType;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "opc_server_url", length = 255)
    private String opcServerUrl;

    @Column(name = "opc_security_policy", length = 50)
    private String opcSecurityPolicy;

    @Column(name = "opc_security_mode", length = 50)
    private String opcSecurityMode;

    @Column(name = "opc_username", length = 100)
    private String opcUsername;

    @Column(name = "opc_password", length = 100)
    private String opcPassword;

    @Column(name = "opc_session_timeout")
    private Integer opcSessionTimeout;

    /** OPC UA 周期性读点间隔（毫秒），与 Modbus 的 modbus_scan_interval 对应。 */
    @Column(name = "opc_scan_interval")
    private Integer opcScanInterval;

    @Column(name = "opc_namespace", length = 100)
    private String opcNamespace;

    @Column(name = "modbus_host", length = 100)
    private String modbusHost;

    @Column(name = "modbus_port")
    private Integer modbusPort;

    @Column(name = "modbus_timeout")
    private Integer modbusTimeout;

    @Column(name = "modbus_scan_interval")
    private Integer modbusScanInterval;

    @Column(name = "http_url", length = 500)
    private String httpUrl;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "http_headers", length = 2000)
    private String httpHeaders;

    @Column(name = "http_body", length = 4000)
    private String httpBody;

    @Column(name = "http_timeout")
    private Integer httpTimeout;

    @Column(name = "http_scan_interval")
    private Integer httpScanInterval;

    @Column(name = "http_data_path", length = 500)
    private String httpDataPath;

    @Column(name = "kafka_enabled")
    private Boolean kafkaEnabled;

    @Column(name = "kafka_cluster_url", length = 255)
    private String kafkaClusterUrl;

    @Column(name = "kafka_bootstrap_servers", length = 255)
    private String kafkaBootstrapServers;

    @Column(name = "kafka_topic", length = 100)
    private String kafkaTopic;

    @Column(name = "kafka_key", length = 255)
    private String kafkaKey;

    @Column(name = "kafka_acks", length = 10)
    private String kafkaAcks;

    @Column(name = "kafka_retries")
    private Integer kafkaRetries;

    @Column(name = "kafka_batch_size")
    private Integer kafkaBatchSize;

    @Column(name = "kafka_linger_ms")
    private Integer kafkaLingerMs;

    @Column(name = "kafka_buffer_memory")
    private Long kafkaBufferMemory;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<TaskPoint> points = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "STOPPED";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}