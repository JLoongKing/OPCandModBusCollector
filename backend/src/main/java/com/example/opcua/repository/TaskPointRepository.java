package com.example.opcua.repository;

import com.example.opcua.entity.TaskPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskPointRepository extends JpaRepository<TaskPoint, Long> {

    List<TaskPoint> findByTaskId(Long taskId);

    void deleteByTaskId(Long taskId);
}