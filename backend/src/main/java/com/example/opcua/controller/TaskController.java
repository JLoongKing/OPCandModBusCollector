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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    @GetMapping
    public Map<String, Object> getAllTasks() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", taskService.getAllTasks());
        return result;
    }

    /** 分页列表须置于 /{id} 之前，避免 "page" 被当作路径变量。 */
    @GetMapping("/page")
    public Map<String, Object> getTasksPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> result = new HashMap<>();
        Pageable pageable = PageRequest.of(page, size);
        Page<Task> taskPage = taskService.getTasksPage(pageable);

        result.put("success", true);
        result.put("data", taskPage.getContent());
        result.put("total", taskPage.getTotalElements());
        result.put("totalPages", taskPage.getTotalPages());
        result.put("currentPage", page);
        return result;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(@RequestParam(value = "protocol", defaultValue = "OPC_UA") String protocol) {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("点位模板");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"点位名称*", "点位地址*", "设备ID", "点位ID", "比例系数（可选）"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            Row exampleRow1 = sheet.createRow(1);
            Row exampleRow2 = sheet.createRow(2);
            Row exampleRow3 = sheet.createRow(3);
            
            if ("MODBUS".equalsIgnoreCase(protocol)) {
                // Modbus协议示例
                exampleRow1.createCell(0).setCellValue("温度");
                exampleRow1.createCell(1).setCellValue("1;40001");
                exampleRow1.createCell(2).setCellValue("DEV-001");
                exampleRow1.createCell(3).setCellValue("POINT-TEMP-001");
                exampleRow1.createCell(4).setCellValue(1.0);

                exampleRow2.createCell(0).setCellValue("压力");
                exampleRow2.createCell(1).setCellValue("1;40003");
                exampleRow2.createCell(2).setCellValue("DEV-001");
                exampleRow2.createCell(3).setCellValue("POINT-PRESS-001");
                exampleRow2.createCell(4).setCellValue(0.1);

                exampleRow3.createCell(0).setCellValue("运行状态");
                exampleRow3.createCell(1).setCellValue("1;00001");
                exampleRow3.createCell(2).setCellValue("DEV-001");
                exampleRow3.createCell(3).setCellValue("POINT-STATUS-001");
                exampleRow3.createCell(4).setCellValue(1.0);
            } else {
                // OPC UA协议示例
                exampleRow1.createCell(0).setCellValue("温度");
                exampleRow1.createCell(1).setCellValue("ns=2;s=Temperature");
                exampleRow1.createCell(2).setCellValue("DEV-001");
                exampleRow1.createCell(3).setCellValue("POINT-TEMP-001");
                exampleRow1.createCell(4).setCellValue(1.0);

                exampleRow2.createCell(0).setCellValue("转速");
                exampleRow2.createCell(1).setCellValue("ns=2;s=RotationSpeed");
                exampleRow2.createCell(2).setCellValue("DEV-001");
                exampleRow2.createCell(3).setCellValue("POINT-SPEED-001");
                exampleRow2.createCell(4).setCellValue(10.0);

                exampleRow3.createCell(0).setCellValue("报警");
                exampleRow3.createCell(1).setCellValue("ns=2;s=Alarm");
                exampleRow3.createCell(2).setCellValue("DEV-002");
                exampleRow3.createCell(3).setCellValue("POINT-ALARM-001");
                exampleRow3.createCell(4).setCellValue(1.0);
            }

            sheet.setColumnWidth(0, 20 * 256);
            sheet.setColumnWidth(1, 30 * 256);
            sheet.setColumnWidth(2, 20 * 256);
            sheet.setColumnWidth(3, 25 * 256);
            sheet.setColumnWidth(4, 15 * 256);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.close();

            HttpHeaders headersHttp = new HttpHeaders();
            headersHttp.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String fileName = "MODBUS".equalsIgnoreCase(protocol) ? "Modbus点位模板.xlsx" : "OPCUA点位模板.xlsx";
            headersHttp.setContentDispositionFormData("attachment", fileName);

            return ResponseEntity.ok()
                    .headers(headersHttp)
                    .body(out.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** 仅解析 Excel，不绑定任务，供新建任务时前端合并表单。 */
    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importPointsPreview(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<TaskPoint> points = pointService.importFromExcel(file);
            // 移除点位中的id字段，避免前端报错
            points.forEach(point -> point.setId(null));
            result.put("success", true);
            result.put("data", points);
            result.put("message", "解析到 " + points.size() + " 个点位");
        } catch (Exception e) {
            log.error("Excel解析失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "解析失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 直接导入点位到新任务（无需先创建任务）
     */
    @PostMapping(value = "/import-to-new", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importToNewTask(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("protocolType") String protocolType,
            @RequestParam(required = false) String opcServerUrl,
            @RequestParam(required = false) String modbusHost,
            @RequestParam(required = false) Integer modbusPort) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<TaskPoint> points = pointService.importFromExcel(file);
            if (points.isEmpty()) {
                result.put("success", false);
                result.put("message", "未解析到有效点位");
                return result;
            }

            // 创建新任务
            Task task = new Task();
            task.setName(name);
            task.setProtocolType(protocolType);
            task.setStatus("STOPPED");
            
            // 设置协议相关参数
            if ("OPCUA".equals(protocolType) && opcServerUrl != null) {
                task.setOpcServerUrl(opcServerUrl);
            } else if ("MODBUS_TCP".equals(protocolType)) {
                task.setModbusHost(modbusHost != null ? modbusHost : "127.0.0.1");
                task.setModbusPort(modbusPort != null ? modbusPort : 502);
            }

            task.setPoints(points);
            Task saved = taskService.createTask(task);

            result.put("success", true);
            result.put("message", "任务创建成功，已导入" + points.size() + "个点位");
            result.put("data", saved);
        } catch (Exception e) {
            log.error("导入点位到新任务失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "导入失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getTask(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        Optional<Task> task = taskService.getTaskById(id);
        if (task.isPresent()) {
            Task taskData = task.get();
            // 移除点位中的id字段，避免前端报错
            if (taskData.getPoints() != null) {
                taskData.getPoints().forEach(point -> {
                    point.setId(null);
                });
            }
            result.put("success", true);
            result.put("data", taskData);
        } else {
            result.put("success", false);
            result.put("message", "任务不存在");
        }
        return result;
    }

    @PostMapping
    public Map<String, Object> createTask(@RequestBody Task task) {
        Map<String, Object> result = new HashMap<>();
        try {
            Task saved = taskService.createTask(task);
            result.put("success", true);
            result.put("message", "任务创建成功");
            result.put("data", saved);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", "参数错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("任务创建失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 创建任务并导入点位（支持Excel导入或直接提交点位数据）
     */
    @PostMapping("/create-with-points")
    public Map<String, Object> createTaskWithPoints(@RequestBody Task task) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 直接调用已有的createTask方法，它已经支持保存任务和点位
            Task saved = taskService.createTask(task);
            int pointCount = saved.getPoints() != null ? saved.getPoints().size() : 0;
            result.put("success", true);
            result.put("message", "任务创建成功，已导入" + pointCount + "个点位");
            result.put("data", saved);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", "参数错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("任务创建失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
        }
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateTask(@PathVariable Long id, @RequestBody Task task) {
        Map<String, Object> result = new HashMap<>();
        try {
            Task updated = taskService.updateTask(id, task);
            result.put("success", true);
            result.put("message", "任务更新成功");
            result.put("data", updated);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteTask(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            taskService.deleteTask(id);
            result.put("success", true);
            result.put("message", "任务删除成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }

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

    @GetMapping("/{id}/data")
    public Map<String, Object> getTaskData(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        List<DataRecord> records = dataRecordRepository.findByTaskIdOrderByTimestampDesc(id);
        result.put("success", true);
        result.put("data", records.stream().limit(100).collect(Collectors.toList()));
        return result;
    }

    @GetMapping("/{id}/data/chart")
    public Map<String, Object> getTaskChartData(
            @PathVariable Long id,
            @RequestParam(defaultValue = "120") int seconds) {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusSeconds(seconds);

        List<DataRecord> records = dataRecordRepository.findByTaskIdAndTimestampBetween(id, startTime, endTime);

        Map<String, List<Map<String, Object>>> chartData = new HashMap<>();
        for (DataRecord record : records) {
            String pointName = record.getPointName();
            Map<String, Object> point = new HashMap<>();
            point.put("timestamp", record.getTimestamp().toString());
            point.put("value", record.getValue());
            chartData.computeIfAbsent(pointName, k -> new ArrayList<>()).add(point);
        }
        for (List<Map<String, Object>> series : chartData.values()) {
            series.sort(Comparator.comparing(m -> String.valueOf(m.get("timestamp"))));
        }

        result.put("success", true);
        result.put("data", chartData);
        return result;
    }

    @GetMapping("/{id}/statistics")
    public Map<String, Object> getTaskStatistics(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        List<DataRecord> records = dataRecordRepository.findByTaskIdOrderByTimestampDesc(id);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecords", records.size());
        stats.put("pointNames", records.stream()
                .map(DataRecord::getPointName)
                .distinct()
                .collect(Collectors.toList()));
        stats.put("isRunning", taskExecutorService.isTaskRunning(id));

        if (!records.isEmpty()) {
            stats.put("latestRecord", records.get(0));
        }

        result.put("success", true);
        result.put("data", stats);
        return result;
    }

    @PostMapping(value = "/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importPoints(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<TaskPoint> points = pointService.importFromExcel(file);
            Task updated = taskService.appendImportedPoints(id, points);
            result.put("success", true);
            result.put("data", updated);
            result.put("message", "已追加 " + points.size() + " 个点位到任务");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "导入失败: " + e.getMessage());
        }
        return result;
    }
}
