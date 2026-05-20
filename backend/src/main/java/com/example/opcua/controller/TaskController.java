package com.example.opcua.controller;

import com.example.opcua.entity.DataRecord;
import com.example.opcua.entity.Task;
import com.example.opcua.entity.TaskPoint;
import com.example.opcua.repository.DataRecordRepository;
import com.example.opcua.service.PointService;
import com.example.opcua.service.TaskExecutorService;
import com.example.opcua.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * 任务管理控制器
 * <p>
 * 提供任务的增删改查、启动停止、数据查询、模板导入导出等功能
 * 支持 OPC UA 和 Modbus 两种协议的任务管理
 *
 * @author Trae AI
 * @version 1.0
 * @since 2026-05-19
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskExecutorService taskExecutorService;

    @Autowired
    private DataRecordRepository dataRecordRepository;

    @Autowired
    private PointService pointService;

    /**
     * 获取所有任务列表
     *
     * @return 包含所有任务的响应结果
     */
    @GetMapping
    public Map<String, Object> getAllTasks() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", taskService.getAllTasks());
        return result;
    }

    /**
     * 分页获取任务列表
     *
     * @param page 页码（从0开始）
     * @param size 每页数量
     * @return 分页任务列表
     */
    @GetMapping("/page")
    public Map<String, Object> getTasksByPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> result = new HashMap<>();
        Pageable pageable = PageRequest.of(page, size);
        Page<Task> taskPage = taskService.getTasksPage(pageable);
        result.put("success", true);
        result.put("data", taskPage.getContent());
        result.put("total", taskPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 获取单个任务详情
     *
     * @param id 任务ID
     * @return 任务详情
     */
    @GetMapping("/{id}")
    public Map<String, Object> getTaskById(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Optional<Task> taskOptional = taskService.getTaskById(id);
            if (taskOptional.isPresent()) {
                Task task = taskOptional.get();
                result.put("success", true);
                result.put("data", task);
            } else {
                result.put("success", false);
                result.put("message", "任务不存在");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取任务失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 创建新任务
     *
     * @param task 任务对象
     * @return 创建结果
     */
    @PostMapping
    public Map<String, Object> createTask(@RequestBody Task task) {
        Map<String, Object> result = new HashMap<>();
        try {
            Task createdTask = taskService.createTask(task);
            result.put("success", true);
            result.put("message", "任务创建成功");
            result.put("data", createdTask);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "任务创建失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 更新任务信息
     *
     * @param id   任务ID
     * @param task 更新后的任务对象
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public Map<String, Object> updateTask(@PathVariable Long id, @RequestBody Task task) {
        Map<String, Object> result = new HashMap<>();
        try {
            Task updatedTask = taskService.updateTask(id, task);
            result.put("success", true);
            result.put("message", "任务更新成功");
            result.put("data", updatedTask);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "任务更新失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 删除任务
     *
     * @param id 任务ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteTask(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            taskService.deleteTask(id);
            result.put("success", true);
            result.put("message", "任务删除成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "任务删除失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 启动任务
     *
     * @param id 任务ID
     * @return 启动结果
     */
    @PostMapping("/{id}/start")
    public Map<String, Object> startTask(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Task task = taskService.startTask(id);
            result.put("success", true);
            result.put("message", "任务已启动");
            result.put("data", task);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "启动失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 停止任务
     *
     * @param id 任务ID
     * @return 停止结果
     */
    @PostMapping("/{id}/stop")
    public Map<String, Object> stopTask(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Task task = taskService.stopTask(id);
            result.put("success", true);
            result.put("message", "任务已停止");
            result.put("data", task);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "停止失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取任务历史数据
     *
     * @param id 任务ID
     * @return 最近100条历史数据
     */
    @GetMapping("/{id}/data")
    public Map<String, Object> getTaskData(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        List<DataRecord> records = dataRecordRepository.findByTaskIdOrderByTimestampDesc(id);
        log.info("查询到任务 {} 的数据记录数: {}", id, records.size());
        
        // 转换为前端友好的格式
        List<Map<String, Object>> dataList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        for (DataRecord record : records) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("id", record.getId());
            dataMap.put("taskId", record.getTaskId());
            dataMap.put("taskName", record.getTaskName() != null ? record.getTaskName() : "");
            dataMap.put("pointName", record.getPointName() != null ? record.getPointName() : "");
            dataMap.put("protocolType", record.getProtocolType() != null ? record.getProtocolType() : "");
            dataMap.put("address", record.getAddress() != null ? record.getAddress() : "");
            dataMap.put("value", record.getValue() != null ? record.getValue() : "");
            dataMap.put("dataType", record.getDataType() != null ? record.getDataType() : "");
            dataMap.put("timestamp", record.getTimestamp() != null ? record.getTimestamp().format(formatter) : "");
            dataList.add(dataMap);
        }
        
        if (!dataList.isEmpty()) {
            log.info("第一条记录: pointName={}, value={}, timestamp={}", 
                    dataList.get(0).get("pointName"), dataList.get(0).get("value"), dataList.get(0).get("timestamp"));
        }
        
        result.put("success", true);
        result.put("data", dataList.stream().limit(100).collect(Collectors.toList()));
        return result;
    }

    /**
     * 获取任务图表数据
     *
     * @param id      任务ID
     * @param seconds 时间范围（秒）
     * @return 图表数据
     */
    @GetMapping("/{id}/data/chart")
    public Map<String, Object> getTaskChartData(
            @PathVariable Long id,
            @RequestParam(defaultValue = "120") int seconds) {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusSeconds(seconds);

        List<DataRecord> records = dataRecordRepository.findByTaskIdAndTimestampBetweenOrderByTimestampDesc(
                id, startTime, endTime);
        
        log.info("查询任务 {} 图表数据: 时间范围 {} 秒, 记录数: {}", id, seconds, records.size());
        if (!records.isEmpty()) {
            log.info("第一条记录: pointName={}, value={}, timestamp={}", 
                    records.get(0).getPointName(), records.get(0).getValue(), records.get(0).getTimestamp());
        }

        // 过滤掉无效记录（时间戳为null或值为null的记录）
        List<DataRecord> validRecords = records.stream()
                .filter(record -> record.getTimestamp() != null)
                .filter(record -> record.getValue() != null && !record.getValue().equals("null"))
                .collect(Collectors.toList());
        
        log.info("有效记录数: {}", validRecords.size());

        // 按点位分组
        Map<String, List<DataRecord>> pointGroups = validRecords.stream()
                .collect(Collectors.groupingBy(DataRecord::getPointName));
        
        log.info("点位分组数: {}", pointGroups.size());

        Map<String, Object> chartData = new HashMap<>();
        List<String> timestamps = new ArrayList<>();

        // 提取时间戳
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (!validRecords.isEmpty()) {
            timestamps = validRecords.stream()
                    .map(record -> record.getTimestamp().format(formatter))
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
            
            log.info("时间戳数量: {}", timestamps.size());
            if (!timestamps.isEmpty()) {
                log.info("时间戳范围: {} 到 {}", timestamps.get(0), timestamps.get(timestamps.size() - 1));
            }
        }

        // 确保时间戳不为null
        if (timestamps == null) {
            timestamps = new ArrayList<>();
        }
        chartData.put("timestamps", timestamps);

        // 为每个点位创建数据系列
        for (Map.Entry<String, List<DataRecord>> entry : pointGroups.entrySet()) {
            String pointName = entry.getKey();
            List<DataRecord> pointRecords = entry.getValue();
            
            log.info("点位 {} 的有效记录数: {}", pointName, pointRecords.size());

            List<Object> values = new ArrayList<>();
            for (String timestamp : timestamps) {
                DataRecord record = pointRecords.stream()
                        .filter(r -> r.getTimestamp().format(formatter).equals(timestamp))
                        .findFirst().orElse(null);
                
                // 如果没有找到记录，返回空字符串而不是null
                Object value = record != null ? record.getValue() : "";
                values.add(value);
                
                if (record != null) {
                    log.debug("点位 {} 在时间 {} 的值: {}", pointName, timestamp, value);
                }
            }

            chartData.put(pointName, values);
            log.info("点位 {} 的数据系列长度: {}", pointName, values.size());
        }

        result.put("success", true);
        result.put("data", chartData);
        log.info("返回图表数据: timestamps={}, series={}", 
                chartData.get("timestamps"), chartData.keySet().stream().filter(k -> !"timestamps".equals(k)).collect(Collectors.toList()));
        return result;
    }
    
    /**
     * 获取点位详情数据
     * 
     * @param pointName 点位名称
     * @param seconds 时间范围（秒）
     * @return 点位详情数据
     */
    @GetMapping("/point/detail")
    public Map<String, Object> getPointDetail(
            @RequestParam String pointName,
            @RequestParam(defaultValue = "3600") int seconds) {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusSeconds(seconds);

        List<DataRecord> records = dataRecordRepository.findByPointNameAndTimestampBetween(
                pointName, startTime, endTime);
        
        log.info("查询点位 {} 详情数据: 时间范围 {} 秒, 记录数: {}", pointName, seconds, records.size());
        if (!records.isEmpty()) {
            log.info("第一条记录: value={}, timestamp={}", 
                    records.get(0).getValue(), records.get(0).getTimestamp());
        }

        // 转换为前端友好的格式
        List<Map<String, Object>> dataList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        for (DataRecord record : records) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("id", record.getId());
            dataMap.put("taskId", record.getTaskId());
            dataMap.put("taskName", record.getTaskName());
            dataMap.put("pointName", record.getPointName());
            dataMap.put("protocolType", record.getProtocolType());
            dataMap.put("address", record.getAddress());
            dataMap.put("value", record.getValue());
            dataMap.put("dataType", record.getDataType());
            dataMap.put("timestamp", record.getTimestamp() != null ? record.getTimestamp().format(formatter) : null);
            dataList.add(dataMap);
        }

        result.put("success", true);
        result.put("data", dataList);
        return result;
    }
    
    /**
     * 获取任务统计数据
     * 
     * @param id 任务ID
     * @return 统计数据
     */
    @GetMapping("/{id}/statistics")
    public Map<String, Object> getTaskStatistics(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        // 查询最近1小时的数据
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(1);
        
        List<DataRecord> records = dataRecordRepository.findByTaskIdAndTimestampBetweenOrderByTimestampDesc(
                id, startTime, endTime);
        
        log.info("查询任务 {} 统计数据: 最近1小时, 记录数: {}", id, records.size());
        
        // 过滤掉无效记录
        List<DataRecord> validRecords = records.stream()
                .filter(record -> record.getTimestamp() != null)
                .filter(record -> record.getValue() != null && !record.getValue().equals("null"))
                .collect(Collectors.toList());
        
        log.info("有效记录数: {}", validRecords.size());
        
        // 按点位分组
        Map<String, List<DataRecord>> pointGroups = validRecords.stream()
                .collect(Collectors.groupingBy(DataRecord::getPointName));
        
        Map<String, Object> statisticsData = new HashMap<>();
        List<Map<String, Object>> pointStatsList = new ArrayList<>();
        
        for (Map.Entry<String, List<DataRecord>> entry : pointGroups.entrySet()) {
            String pointName = entry.getKey();
            List<DataRecord> pointRecords = entry.getValue();
            
            if (pointRecords.isEmpty()) {
                continue;
            }
            
            // 计算统计值
            List<Double> values = pointRecords.stream()
                    .map(r -> {
                        try {
                            return Double.parseDouble(r.getValue());
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            Map<String, Object> pointStats = new HashMap<>();
            pointStats.put("pointName", pointName);
            pointStats.put("recordCount", pointRecords.size());
            pointStats.put("validCount", values.size());
            
            if (!values.isEmpty()) {
                double min = Collections.min(values);
                double max = Collections.max(values);
                double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double latest = values.get(values.size() - 1);
                
                pointStats.put("min", min);
                pointStats.put("max", max);
                pointStats.put("avg", avg);
                pointStats.put("latest", latest);
                pointStats.put("unit", ""); // 可以根据实际情况添加单位
            } else {
                pointStats.put("min", 0);
                pointStats.put("max", 0);
                pointStats.put("avg", 0);
                pointStats.put("latest", 0);
                pointStats.put("unit", "");
            }
            
            // 获取最新的时间戳
            DataRecord latestRecord = pointRecords.get(0);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String latestTime = latestRecord.getTimestamp() != null ? latestRecord.getTimestamp().format(formatter) : "";
            pointStats.put("timestamp", latestTime); // 修改为timestamp字段，而不是latestTime
            pointStats.put("latestTime", latestTime); // 保留原来的字段，兼容旧版本
            
            pointStatsList.add(pointStats);
        }
        
        statisticsData.put("taskId", id);
        statisticsData.put("totalRecords", records.size());
        statisticsData.put("validRecords", validRecords.size());
        statisticsData.put("pointCount", pointGroups.size());
        statisticsData.put("pointStatistics", pointStatsList);
        statisticsData.put("updateTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        result.put("success", true);
        result.put("data", statisticsData);
        return result;
    }

    /**
     * 下载任务点位模板
     *
     * @param id 任务ID
     * @return 模板文件
     */
    @GetMapping("/{id}/template")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable Long id) {
        try {
            Optional<Task> taskOptional = taskService.getTaskById(id);
            if (!taskOptional.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            Task task = taskOptional.get();

            return downloadTemplateByProtocol(task.getProtocolType());
        } catch (Exception e) {
            log.error("下载模板失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 根据协议类型下载点位模板
     * 
     * @param protocol 协议类型（OPC_UA或MODBUS）
     * @return 模板文件
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplateByProtocol(@RequestParam String protocol) {
        try {
            String[] headers;

            // 根据协议类型设置不同的模板表头
            if ("MODBUS".equalsIgnoreCase(protocol)) {
                headers = new String[]{"点位名称*", "点位地址*", "设备ID", "点位ID", "数据类型", "位数", "比例系数（可选）"};
            } else {
                headers = new String[]{"点位名称*", "点位地址*", "设备ID", "点位ID", "比例系数（可选）"};
            }

            // 创建Excel工作簿
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("点位模板");

            // 创建表头
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 添加示例数据
            if ("OPC_UA".equalsIgnoreCase(protocol)) {
                // OPC UA示例数据
                Row row1 = sheet.createRow(1);
                row1.createCell(0).setCellValue("温度传感器");
                row1.createCell(1).setCellValue("Temperature");
                row1.createCell(2).setCellValue("DEVICE_001");
                row1.createCell(3).setCellValue("Node_001");
                row1.createCell(4).setCellValue(1.0);
                
                Row row2 = sheet.createRow(2);
                row2.createCell(0).setCellValue("压力传感器");
                row2.createCell(1).setCellValue("Pressure");
                row2.createCell(2).setCellValue("DEVICE_001");
                row2.createCell(3).setCellValue("Node_002");
                row2.createCell(4).setCellValue(1.0);
                
                Row row3 = sheet.createRow(3);
                row3.createCell(0).setCellValue("流量传感器");
                row3.createCell(1).setCellValue("FlowRate");
                row3.createCell(2).setCellValue("DEVICE_002");
                row3.createCell(3).setCellValue("Node_003");
                row3.createCell(4).setCellValue(1.0);
            } else if ("MODBUS".equalsIgnoreCase(protocol)) {
                // MODBUS示例数据
                Row row1 = sheet.createRow(1);
                row1.createCell(0).setCellValue("温度传感器");
                row1.createCell(1).setCellValue("4x0001");
                row1.createCell(2).setCellValue("DEVICE_001");
                row1.createCell(3).setCellValue("Node_001");
                row1.createCell(4).setCellValue("float");
                row1.createCell(5).setCellValue(32);
                row1.createCell(6).setCellValue(1.0);
                
                Row row2 = sheet.createRow(2);
                row2.createCell(0).setCellValue("压力传感器");
                row2.createCell(1).setCellValue("4x0002");
                row2.createCell(2).setCellValue("DEVICE_001");
                row2.createCell(3).setCellValue("Node_002");
                row2.createCell(4).setCellValue("float");
                row2.createCell(5).setCellValue(32);
                row2.createCell(6).setCellValue(1.0);
            }

            // 再次调整列宽以适应示例数据
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 写入输出流
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            // 设置响应头
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            responseHeaders.setContentDispositionFormData("attachment", "task_points_template.xlsx");

            return ResponseEntity.ok()
                    .headers(responseHeaders)
                    .body(outputStream.toByteArray());

        } catch (Exception e) {
            log.error("下载模板失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 导入点位数据
     *
     * @param file 导入的Excel文件
     * @param id   任务ID
     * @return 导入结果
     */
    @PostMapping("/{id}/import")
    public Map<String, Object> importPoints(
            @RequestParam("file") MultipartFile file,
            @PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Optional<Task> taskOptional = taskService.getTaskById(id);
            if (!taskOptional.isPresent()) {
                result.put("success", false);
                result.put("message", "任务不存在");
                return result;
            }
            Task task = taskOptional.get();

            // 读取Excel文件
            Workbook workbook = WorkbookFactory.create(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);

            List<TaskPoint> points = new ArrayList<>();
            Iterator<Row> rowIterator = sheet.iterator();

            // 跳过表头
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            // 读取数据行
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                // 跳过空行
                if (row.getCell(0) == null || row.getCell(0).getStringCellValue().trim().isEmpty()) {
                    continue;
                }

                TaskPoint point = new TaskPoint();
                // 设置任务关联
                Task taskRef = new Task();
                taskRef.setId(id);
                point.setTask(taskRef);
                
                point.setName(row.getCell(0).getStringCellValue().trim());
                String address = row.getCell(1).getStringCellValue().trim();
                
                // 处理OPC UA地址格式，提取命名空间和节点ID
                if ("OPC_UA".equalsIgnoreCase(task.getProtocolType())) {
                    // 匹配ns=2;s=Bu/test格式
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("ns=(\\d+);s=(.+)");
                    java.util.regex.Matcher matcher = pattern.matcher(address);
                    if (matcher.matches()) {
                        // 如果地址包含命名空间，提取出来
                        String namespace = matcher.group(1);
                        String nodeId = matcher.group(2);
                        point.setAddress(nodeId); // 只保存节点ID部分
                        // 如果任务没有设置命名空间，设置为提取的命名空间
                            if (task.getOpcNamespace() == null || task.getOpcNamespace().isEmpty()) {
                                task.setOpcNamespace(namespace);
                                taskService.updateTask(task.getId(), task); // 更新任务的命名空间
                            }
                    } else {
                        // 如果地址不包含命名空间，直接保存
                        point.setAddress(address);
                    }
                } else {
                    // 其他协议直接保存地址
                    point.setAddress(address);
                }

                // 处理可选字段
                if (row.getCell(2) != null) {
                    point.setDevId(row.getCell(2).getStringCellValue().trim());
                }
                if (row.getCell(3) != null) {
                    point.setNodeId(row.getCell(3).getStringCellValue().trim());
                }
                if (row.getCell(4) != null && "MODBUS".equalsIgnoreCase(task.getProtocolType())) {
                    point.setDataType(row.getCell(4).getStringCellValue().trim());
                }
                if (row.getCell(5) != null && "MODBUS".equalsIgnoreCase(task.getProtocolType())) {
                    point.setBitLength((int) row.getCell(5).getNumericCellValue());
                }
                if (row.getCell(6) != null) {
                    point.setScaleFactor(row.getCell(6).getNumericCellValue());
                }

                points.add(point);
            }

            workbook.close();

            // 保存点位
            if (!points.isEmpty()) {
                pointService.savePoints(points);
                result.put("success", true);
                result.put("message", "成功导入 " + points.size() + " 个点位");
                result.put("data", points);
            } else {
                result.put("success", false);
                result.put("message", "未找到有效点位数据");
            }

        } catch (Exception e) {
            log.error("导入点位失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "导入失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 预览导入的点位数据
     *
     * @param file 导入的Excel文件
     * @return 预览结果
     */
    @PostMapping("/preview-import")
    public Map<String, Object> previewImport(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 读取Excel文件
            Workbook workbook = WorkbookFactory.create(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);

            List<Map<String, Object>> points = new ArrayList<>();
            Iterator<Row> rowIterator = sheet.iterator();

            // 跳过表头
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            // 读取数据行
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                // 跳过空行
                if (row.getCell(0) == null || row.getCell(0).getStringCellValue().trim().isEmpty()) {
                    continue;
                }

                Map<String, Object> point = new HashMap<>();
                point.put("pointName", row.getCell(0).getStringCellValue().trim());
                point.put("address", row.getCell(1).getStringCellValue().trim());

                // 处理可选字段
                if (row.getCell(2) != null) {
                    point.put("devId", row.getCell(2).getStringCellValue().trim());
                }
                if (row.getCell(3) != null) {
                    point.put("nodeId", row.getCell(3).getStringCellValue().trim());
                }
                if (row.getCell(4) != null) {
                    point.put("dataType", row.getCell(4).getStringCellValue().trim());
                }
                if (row.getCell(5) != null) {
                    point.put("bitLength", (int) row.getCell(5).getNumericCellValue());
                }
                if (row.getCell(6) != null) {
                    point.put("scaleFactor", row.getCell(6).getNumericCellValue());
                }

                points.add(point);
            }

            workbook.close();

            result.put("success", true);
            result.put("message", "成功预览 " + points.size() + " 个点位");
            result.put("data", points);

        } catch (Exception e) {
            log.error("预览导入失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "预览失败: " + e.getMessage());
        }
        return result;
    }
}
