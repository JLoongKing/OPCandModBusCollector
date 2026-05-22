package com.example.opcua.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataRecord {
    private Long id;
    private Long taskId;
    private String taskName;
    private String pointName;
    private String protocolType;
    private String address;
    private String value;
    private String dataType;
    private LocalDateTime timestamp;
}