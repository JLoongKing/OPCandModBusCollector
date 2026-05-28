package com.example.opcua.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@Entity
@Table(name = "task_point")
@NoArgsConstructor
@AllArgsConstructor
public class TaskPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "address", length = 255, nullable = false)
    private String address;

    @Column(name = "dev_id", length = 255)
    private String devId;

    @Column(name = "node_id", length = 255)
    private String nodeId;

    @Column(name = "scale_factor", precision = 10, scale = 4)
    private Double scaleFactor = 1.0;

    @Column(name = "data_type", length = 20)
    private String dataType = "float";

    @Column(name = "bit_length")
    private Integer bitLength = 32;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "json_path", length = 500)
    private String jsonPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    @JsonIgnore
    private Task task;
}