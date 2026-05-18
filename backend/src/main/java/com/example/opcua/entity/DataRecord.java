package com.example.opcua.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "data_record")
@NoArgsConstructor
@AllArgsConstructor
public class DataRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "task_name", length = 100)
    private String taskName;

    @Column(name = "point_name", length = 100)
    private String pointName;

    @Column(name = "protocol_type", length = 20)
    private String protocolType;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "record_value", length = 255)
    private String value;

    @Column(name = "data_type", length = 50)
    private String dataType;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}