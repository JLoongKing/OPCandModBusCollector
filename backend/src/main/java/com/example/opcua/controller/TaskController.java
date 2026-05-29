package com.example.opcua.controller;

import com.example.opcua.entity.Task;
import com.example.opcua.entity.TaskPoint;
import com.example.opcua.service.PointService;
import com.example.opcua.service.TaskExecutorService;
import com.example.opcua.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;
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
     * 获取单元格值作为字符串，自动处理不同类型的单元格
     * 
     * @param cell 单元格
     * @return 单元格值的字符串表示
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        double numericValue = cell.getNumericCellValue();
                        if (numericValue == (long) numericValue) {
                            return String.valueOf((long) numericValue);
                        } else {
                            return String.valueOf(numericValue);
                        }
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cellValue = evaluator.evaluate(cell);
                    return getCellValueFromCellValue(cellValue);
                case BLANK:
                    return "";
                default:
                    return "";
            }
        } catch (Exception e) {
            log.error("获取单元格值失败", e);
            return "";
        }
    }
    
    /**
     * 获取CellValue对象的值作为字符串
     * 
     * @param cellValue CellValue对象
     * @return 字符串值
     */
    private String getCellValueFromCellValue(CellValue cellValue) {
        if (cellValue == null) {
            return "";
        }
        
        switch (cellValue.getCellType()) {
            case STRING:
                return cellValue.getStringValue();
            case NUMERIC:
                double numericValue = cellValue.getNumberValue();
                if (numericValue == (long) numericValue) {
                    return String.valueOf((long) numericValue);
                } else {
                    return String.valueOf(numericValue);
                }
            case BOOLEAN:
                return String.valueOf(cellValue.getBooleanValue());
            default:
                return "";
        }
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
            System.out.println("[日志] 收到启动任务请求，任务ID: " + id);
            Task task = taskService.startTask(id);
            System.out.println("[日志] 任务启动成功，任务ID: " + id + ", 名称: " + task.getName() + ", 协议: " + task.getProtocolType());
            result.put("success", true);
            result.put("message", "任务已启动");
            result.put("data", task);
        } catch (Exception e) {
            System.err.println("[错误] 任务启动失败，任务ID: " + id + ", 错误: " + e.getMessage());
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
        List<TaskExecutorService.DataCacheEntry> entries = taskExecutorService.getCacheData(id, null);
        log.info("任务 {} 从缓存查询到数据记录数: {}", id, entries.size());

        List<Map<String, Object>> dataList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (TaskExecutorService.DataCacheEntry entry : entries) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("id", System.currentTimeMillis());
            dataMap.put("taskId", entry.getTaskId());
            dataMap.put("taskName", entry.getTaskName() != null ? entry.getTaskName() : "");
            dataMap.put("pointName", entry.getPointName() != null ? entry.getPointName() : "");
            dataMap.put("protocolType", entry.getProtocolType() != null ? entry.getProtocolType() : "");
            dataMap.put("address", entry.getAddress() != null ? entry.getAddress() : "");
            dataMap.put("value", entry.getValue() != null ? entry.getValue() : "");
            dataMap.put("dataType", entry.getDataType() != null ? entry.getDataType() : "");
            dataMap.put("timestamp", entry.getTimestamp() != null ? entry.getTimestamp().format(formatter) : "");
            dataList.add(dataMap);
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
        List<TaskExecutorService.DataCacheEntry> entries = taskExecutorService.getCacheData(id, (long) seconds);
        log.info("任务 {} 从缓存查询图表数据: 时间范围 {} 秒, 记录数: {}", id, seconds, entries.size());

        Map<String, List<TaskExecutorService.DataCacheEntry>> pointGroups = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getPointName() != null ? e.getPointName() : "unknown"));

        Map<String, Object> chartData = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        List<String> timestamps = entries.stream()
                .map(e -> e.getTimestamp())
                .filter(t -> t != null)
                .map(t -> t.format(formatter))
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        chartData.put("timestamps", timestamps);

        for (Map.Entry<String, List<TaskExecutorService.DataCacheEntry>> groupEntry : pointGroups.entrySet()) {
            String pointName = groupEntry.getKey();
            List<TaskExecutorService.DataCacheEntry> pointEntries = groupEntry.getValue();
            List<Object> values = new ArrayList<>();
            for (String ts : timestamps) {
                String val = pointEntries.stream()
                        .filter(e -> e.getTimestamp() != null && e.getTimestamp().format(formatter).equals(ts))
                        .map(e -> e.getValue())
                        .findFirst().orElse("");
                values.add(val);
            }
            chartData.put(pointName, values);
        }

        result.put("success", true);
        result.put("data", chartData);
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
            @RequestParam(defaultValue = "120") int seconds) {
        Map<String, Object> result = new HashMap<>();
        List<TaskExecutorService.DataCacheEntry> entries = taskExecutorService.getCacheDataByPointName(pointName, (long) seconds);
        log.info("点位 {} 从缓存查询详情数据: 时间范围 {} 秒, 记录数: {}", pointName, seconds, entries.size());

        List<Map<String, Object>> dataList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (TaskExecutorService.DataCacheEntry entry : entries) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("id", System.currentTimeMillis());
            dataMap.put("taskId", entry.getTaskId());
            dataMap.put("taskName", entry.getTaskName());
            dataMap.put("pointName", entry.getPointName());
            dataMap.put("protocolType", entry.getProtocolType());
            dataMap.put("address", entry.getAddress());
            dataMap.put("value", entry.getValue());
            dataMap.put("dataType", entry.getDataType());
            dataMap.put("timestamp", entry.getTimestamp() != null ? entry.getTimestamp().format(formatter) : null);
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
        List<TaskExecutorService.DataCacheEntry> entries = taskExecutorService.getCacheData(id, 3600L);
        log.info("任务 {} 从缓存查询统计数据: 记录数: {}", id, entries.size());

        Map<String, List<TaskExecutorService.DataCacheEntry>> pointGroups = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getPointName() != null ? e.getPointName() : "unknown"));

        Map<String, Object> statisticsData = new HashMap<>();
        List<Map<String, Object>> pointStatsList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (Map.Entry<String, List<TaskExecutorService.DataCacheEntry>> groupEntry : pointGroups.entrySet()) {
            String pointName = groupEntry.getKey();
            List<TaskExecutorService.DataCacheEntry> pointEntries = groupEntry.getValue();

            if (pointEntries.isEmpty()) {
                continue;
            }

            List<Double> values = pointEntries.stream()
                    .map(e -> {
                        try {
                            return Double.parseDouble(e.getValue());
                        } catch (NumberFormatException ex) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            Map<String, Object> pointStats = new HashMap<>();
            pointStats.put("pointName", pointName);
            pointStats.put("recordCount", pointEntries.size());
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
                pointStats.put("unit", "");
            } else {
                pointStats.put("min", 0);
                pointStats.put("max", 0);
                pointStats.put("avg", 0);
                pointStats.put("latest", 0);
                pointStats.put("unit", "");
            }

            TaskExecutorService.DataCacheEntry latestEntry = pointEntries.get(pointEntries.size() - 1);
            String latestTime = latestEntry.getTimestamp() != null ? latestEntry.getTimestamp().format(formatter) : "";
            pointStats.put("timestamp", latestTime);
            pointStats.put("latestTime", latestTime);

            pointStatsList.add(pointStats);
        }

        statisticsData.put("taskId", id);
        statisticsData.put("totalRecords", taskExecutorService.getDataCount(id));
        statisticsData.put("validRecords", entries.size());
        statisticsData.put("pointCount", pointGroups.size());
        statisticsData.put("pointStatistics", pointStatsList);
        statisticsData.put("updateTime", LocalDateTime.now().format(formatter));

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
                headers = new String[]{"点位名称*", "点位地址*", "设备ID", "点位ID", "数据类型", "位数", "比例系数（可选）", "地址是否-1", "bit读取位"};
            } else if ("HTTP".equalsIgnoreCase(protocol)) {
                headers = new String[]{"点位名称*", "JSON路径*", "设备ID", "点位ID", "比例系数（可选）"};
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
                row1.createCell(7).setCellValue("否");
                row1.createCell(8).setCellValue("");
                
                Row row2 = sheet.createRow(2);
                row2.createCell(0).setCellValue("压力传感器");
                row2.createCell(1).setCellValue("4x0002");
                row2.createCell(2).setCellValue("DEVICE_001");
                row2.createCell(3).setCellValue("Node_002");
                row2.createCell(4).setCellValue("float");
                row2.createCell(5).setCellValue(32);
                row2.createCell(6).setCellValue(1.0);
                row2.createCell(7).setCellValue("否");
                row2.createCell(8).setCellValue("");
            } else if ("HTTP".equalsIgnoreCase(protocol)) {
                Row row1 = sheet.createRow(1);
                row1.createCell(0).setCellValue("温度");
                row1.createCell(1).setCellValue("data.temperature");
                row1.createCell(2).setCellValue("DEVICE_001");
                row1.createCell(3).setCellValue("Node_001");
                row1.createCell(4).setCellValue(1.0);
                
                Row row2 = sheet.createRow(2);
                row2.createCell(0).setCellValue("压力");
                row2.createCell(1).setCellValue("data.pressure");
                row2.createCell(2).setCellValue("DEVICE_001");
                row2.createCell(3).setCellValue("Node_002");
                row2.createCell(4).setCellValue(1.0);
                
                Row row3 = sheet.createRow(3);
                row3.createCell(0).setCellValue("湿度");
                row3.createCell(1).setCellValue("data.humidity");
                row3.createCell(2).setCellValue("DEVICE_002");
                row3.createCell(3).setCellValue("Node_003");
                row3.createCell(4).setCellValue(1.0);
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
        long startTime = System.currentTimeMillis();
        try {
            Optional<Task> taskOptional = taskService.getTaskById(id);
            if (!taskOptional.isPresent()) {
                result.put("success", false);
                result.put("message", "任务不存在");
                return result;
            }
            Task task = taskOptional.get();

            log.info("开始导入点位，任务ID: {}, 文件名: {}, 大小: {} bytes",
                    id, file.getOriginalFilename(), file.getSize());

            Workbook workbook = WorkbookFactory.create(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);

            int totalRows = sheet.getLastRowNum();
            log.info("Excel共 {} 行数据（含表头），开始解析...", totalRows);

            List<TaskPoint> points = new ArrayList<>();
            Iterator<Row> rowIterator = sheet.iterator();

            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            int parseErrors = 0;
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                try {
                    if (row.getCell(0) == null || row.getCell(0).getStringCellValue().trim().isEmpty()) {
                        continue;
                    }

                    TaskPoint point = new TaskPoint();
                    Task taskRef = new Task();
                    taskRef.setId(id);
                    point.setTask(taskRef);

                    point.setName(row.getCell(0).getStringCellValue().trim());
                    String address = row.getCell(1).getStringCellValue().trim();

                    if ("OPC_UA".equalsIgnoreCase(task.getProtocolType())) {
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("ns=(\\d+);s=(.+)");
                        java.util.regex.Matcher matcher = pattern.matcher(address);
                        if (matcher.matches()) {
                            String namespace = matcher.group(1);
                            String nodeId = matcher.group(2);
                            point.setAddress(nodeId);
                            if (task.getOpcNamespace() == null || task.getOpcNamespace().isEmpty()) {
                                task.setOpcNamespace(namespace);
                                taskService.updateTask(task.getId(), task);
                            }
                        } else {
                            point.setAddress(address);
                        }
                    } else {
                        point.setAddress(address);
                    }

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
                    if (row.getCell(7) != null && "MODBUS".equalsIgnoreCase(task.getProtocolType())) {
                        String addrMinusOneStr = getCellValueAsString(row.getCell(7));
                        if (addrMinusOneStr != null && !addrMinusOneStr.isEmpty()) {
                            point.setAddressOffsetMinusOne("是".equals(addrMinusOneStr.trim()) || "true".equalsIgnoreCase(addrMinusOneStr.trim()) || "1".equals(addrMinusOneStr.trim()));
                        }
                    }
                    if (row.getCell(8) != null && "MODBUS".equalsIgnoreCase(task.getProtocolType())) {
                        String bitPosStr = getCellValueAsString(row.getCell(8));
                        if (bitPosStr != null && !bitPosStr.isEmpty()) {
                            try {
                                point.setBitReadPosition(Integer.parseInt(bitPosStr.trim()));
                            } catch (NumberFormatException e) {
                                log.warn("bit读取位解析失败: {}", bitPosStr);
                            }
                        }
                    }

                    points.add(point);
                } catch (Exception e) {
                    parseErrors++;
                    log.warn("解析第 {} 行失败: {}", row.getRowNum() + 1, e.getMessage());
                }
            }

            workbook.close();

            long parseTime = System.currentTimeMillis() - startTime;
            log.info("Excel解析完成，有效点位: {} 个，解析失败: {} 行，耗时: {}ms",
                    points.size(), parseErrors, parseTime);

            if (!points.isEmpty()) {
                pointService.savePointsBatch(points);
                long totalTime = System.currentTimeMillis() - startTime;
                log.info("点位导入完成，共 {} 个点位，总耗时: {}ms", points.size(), totalTime);

                result.put("success", true);
                result.put("message", "成功导入 " + points.size() + " 个点位");
                result.put("count", points.size());
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
    public Map<String, Object> previewImport(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "protocol", required = false, defaultValue = "OPC_UA") String protocol) {
        Map<String, Object> result = new HashMap<>();
        try {
            Workbook workbook = WorkbookFactory.create(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);

            List<Map<String, Object>> points = new ArrayList<>();
            Iterator<Row> rowIterator = sheet.iterator();

            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                if (row.getCell(0) == null || getCellValueAsString(row.getCell(0)).trim().isEmpty()) {
                    continue;
                }

                Map<String, Object> point = new HashMap<>();
                point.put("name", getCellValueAsString(row.getCell(0)).trim());

                if ("HTTP".equalsIgnoreCase(protocol)) {
                    point.put("jsonPath", getCellValueAsString(row.getCell(1)).trim());
                } else {
                    point.put("address", getCellValueAsString(row.getCell(1)).trim());
                }

                if (row.getCell(2) != null) {
                    point.put("devId", getCellValueAsString(row.getCell(2)).trim());
                }
                if (row.getCell(3) != null) {
                    point.put("nodeId", getCellValueAsString(row.getCell(3)).trim());
                }

                if ("MODBUS".equalsIgnoreCase(protocol) || "MODBUS_TCP".equalsIgnoreCase(protocol)) {
                    if (row.getCell(4) != null) {
                        point.put("dataType", getCellValueAsString(row.getCell(4)).trim());
                    }
                    if (row.getCell(5) != null) {
                        String bitLengthStr = getCellValueAsString(row.getCell(5));
                        try {
                            point.put("bitLength", Integer.parseInt(bitLengthStr));
                        } catch (NumberFormatException e) {
                            log.warn("位数解析失败: {}", bitLengthStr);
                        }
                    }
                    if (row.getCell(6) != null) {
                        String scaleFactorStr = getCellValueAsString(row.getCell(6));
                        try {
                            point.put("scaleFactor", Double.parseDouble(scaleFactorStr));
                        } catch (NumberFormatException e) {
                            log.warn("比例系数解析失败: {}", scaleFactorStr);
                        }
                    }
                    if (row.getCell(7) != null) {
                        String addrMinusOneStr = getCellValueAsString(row.getCell(7));
                        if (addrMinusOneStr != null && !addrMinusOneStr.isEmpty()) {
                            point.put("addressOffsetMinusOne", "是".equals(addrMinusOneStr.trim()) || "true".equalsIgnoreCase(addrMinusOneStr.trim()) || "1".equals(addrMinusOneStr.trim()));
                        }
                    }
                    if (row.getCell(8) != null) {
                        String bitPosStr = getCellValueAsString(row.getCell(8));
                        if (bitPosStr != null && !bitPosStr.isEmpty()) {
                            try {
                                point.put("bitReadPosition", Integer.parseInt(bitPosStr.trim()));
                            } catch (NumberFormatException e) {
                                log.warn("bit读取位解析失败: {}", bitPosStr);
                            }
                        }
                    }
                } else {
                    if (row.getCell(4) != null) {
                        String scaleFactorStr = getCellValueAsString(row.getCell(4));
                        try {
                            point.put("scaleFactor", Double.parseDouble(scaleFactorStr));
                        } catch (NumberFormatException e) {
                            log.warn("比例系数解析失败: {}", scaleFactorStr);
                        }
                    }
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
