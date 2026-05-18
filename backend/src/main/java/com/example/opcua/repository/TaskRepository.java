package com.example.opcua.repository;

import com.example.opcua.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatus(String status);

    List<Task> findByProtocolType(String protocolType);

    List<Task> findByNameContaining(String name);
}