package com.example.opcua.service;

import com.example.opcua.entity.Task;
import com.example.opcua.entity.TaskPoint;
import com.example.opcua.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskExecutorService taskExecutorService;

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    public Page<Task> getTasksPage(Pageable pageable) {
        return taskRepository.findAll(pageable);
    }

    public Optional<Task> getTaskById(Long id) {
        return taskRepository.findById(id);
    }

    public List<Task> getTasksByStatus(String status) {
        return taskRepository.findByStatus(status);
    }

    @Transactional
    public Task createTask(Task task) {
        if (task == null) {
            throw new IllegalArgumentException("任务对象不能为空");
        }
        
        // 确保任务名称和协议类型不为空
        if (task.getName() == null || task.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (task.getProtocolType() == null || task.getProtocolType().trim().isEmpty()) {
            throw new IllegalArgumentException("协议类型不能为空");
        }
        
        // 处理点位
        if (task.getPoints() != null) {
            List<TaskPoint> validPoints = new ArrayList<>();
            for (TaskPoint point : task.getPoints()) {
                // 过滤无效点位
            if (point != null && point.getName() != null && !point.getName().trim().isEmpty() &&
                point.getAddress() != null && !point.getAddress().trim().isEmpty()) {
                point.setTask(task);
                // 确保sortOrder有值
                if (point.getSortOrder() == null) {
                    point.setSortOrder(validPoints.size());
                }
                // 自动生成设备ID和点位ID（如果为空）
                if (point.getDevId() == null || point.getDevId().trim().isEmpty()) {
                    point.setDevId("DEV-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8));
                }
                if (point.getNodeId() == null || point.getNodeId().trim().isEmpty()) {
                    point.setNodeId("POINT-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8));
                }
                // 设置默认值
                if (point.getDataType() == null || point.getDataType().trim().isEmpty()) {
                    point.setDataType("float");
                }
                if (point.getBitLength() == null) {
                    point.setBitLength(32);
                }
                if (point.getScaleFactor() == null) {
                    point.setScaleFactor(1.0);
                }
                validPoints.add(point);
            }
            }
            task.setPoints(validPoints);
        }
        
        // 设置默认状态
        if (task.getStatus() == null || task.getStatus().trim().isEmpty()) {
            task.setStatus("STOPPED");
        }
        
        return taskRepository.save(task);
    }

    @Transactional
    public Task updateTask(Long id, Task updatedTask) {
        Task existing = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + id));

        existing.setName(updatedTask.getName());
        existing.setDescription(updatedTask.getDescription());
        existing.setProtocolType(updatedTask.getProtocolType());

        existing.setOpcServerUrl(updatedTask.getOpcServerUrl());
        existing.setOpcSecurityPolicy(updatedTask.getOpcSecurityPolicy());
        existing.setOpcSecurityMode(updatedTask.getOpcSecurityMode());
        existing.setOpcUsername(updatedTask.getOpcUsername());
        existing.setOpcPassword(updatedTask.getOpcPassword());
        existing.setOpcSessionTimeout(updatedTask.getOpcSessionTimeout());
        existing.setOpcScanInterval(updatedTask.getOpcScanInterval());
        existing.setOpcNamespace(updatedTask.getOpcNamespace()); // 添加这行，更新opcNamespace

        existing.setModbusHost(updatedTask.getModbusHost());
        existing.setModbusPort(updatedTask.getModbusPort());
        existing.setModbusTimeout(updatedTask.getModbusTimeout());
        existing.setModbusScanInterval(updatedTask.getModbusScanInterval());

        existing.setKafkaEnabled(updatedTask.getKafkaEnabled());
        existing.setKafkaBootstrapServers(updatedTask.getKafkaBootstrapServers());
        existing.setKafkaTopic(updatedTask.getKafkaTopic());
        existing.setKafkaAcks(updatedTask.getKafkaAcks());
        existing.setKafkaRetries(updatedTask.getKafkaRetries());
        existing.setKafkaBatchSize(updatedTask.getKafkaBatchSize());
        existing.setKafkaLingerMs(updatedTask.getKafkaLingerMs());
        existing.setKafkaBufferMemory(updatedTask.getKafkaBufferMemory());

        if (updatedTask.getPoints() != null) {
            // 完全清空现有点位，确保orphanRemoval生效
            existing.getPoints().clear();
            
            // 强制刷新持久化上下文，确保删除操作被记录
            taskRepository.flush();
            
            int sortOrder = 0;
            for (TaskPoint point : updatedTask.getPoints()) {
                // 不使用前端传来的id，使用新创建的id
                TaskPoint newPoint = new TaskPoint();
                newPoint.setName(point.getName());
                newPoint.setAddress(point.getAddress());
                newPoint.setDevId(point.getDevId());
                newPoint.setNodeId(point.getNodeId());
                newPoint.setDataType(point.getDataType());
                newPoint.setBitLength(point.getBitLength());
                newPoint.setScaleFactor(point.getScaleFactor());
                newPoint.setSortOrder(sortOrder++);
                newPoint.setTask(existing);
                existing.getPoints().add(newPoint);
            }
        }

        return taskRepository.save(existing);
    }

    @Transactional
    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + id));
        if ("RUNNING".equals(task.getStatus())) {
            taskExecutorService.stopTask(id);
        }
        taskRepository.deleteById(id);
    }

    public Task startTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + id));

        if ("RUNNING".equals(task.getStatus())) {
            throw new RuntimeException("任务已在运行中");
        }

        task.setStatus("RUNNING");
        task = taskRepository.save(task);

        taskExecutorService.startTask(task);

        return task;
    }

    public Task stopTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + id));

        if (!"RUNNING".equals(task.getStatus())) {
            throw new RuntimeException("任务未在运行中");
        }

        taskExecutorService.stopTask(id);

        task.setStatus("STOPPED");
        return taskRepository.save(task);
    }

    /**
     * 将 Excel 解析出的点位追加到任务（运行中禁止）。
     */
    @Transactional
    public Task appendImportedPoints(Long taskId, List<TaskPoint> imported) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));
        if ("RUNNING".equals(task.getStatus())) {
            throw new RuntimeException("任务运行中不可导入点位，请先停止任务");
        }
        int nextOrder = task.getPoints().stream()
                .mapToInt(p -> p.getSortOrder() != null ? p.getSortOrder() : 0)
                .max()
                .orElse(-1) + 1;
        for (TaskPoint src : imported) {
            TaskPoint p = new TaskPoint();
            p.setName(src.getName());
            p.setAddress(src.getAddress());
            p.setDevId(src.getDevId());
            p.setNodeId(src.getNodeId());
            p.setDataType(src.getDataType());
            p.setBitLength(src.getBitLength());
            p.setScaleFactor(src.getScaleFactor());
            p.setSortOrder(nextOrder++);
            p.setTask(task);
            task.getPoints().add(p);
        }
        return taskRepository.save(task);
    }
}