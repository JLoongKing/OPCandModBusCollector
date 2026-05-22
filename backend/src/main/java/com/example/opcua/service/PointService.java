package com.example.opcua.service;

import com.example.opcua.entity.TaskPoint;
import com.example.opcua.repository.TaskPointRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Service
public class PointService {
    
    @Autowired
    private TaskPointRepository taskPointRepository;

    public List<TaskPoint> importFromExcel(MultipartFile file) throws IOException {
        List<TaskPoint> points = new ArrayList<>();
        
        log.info("开始解析Excel文件: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());
        
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            log.info("获取到Excel工作表: {}, 行数: {}", sheet.getSheetName(), sheet.getPhysicalNumberOfRows());
            
            Iterator<Row> rowIterator = sheet.iterator();
            
            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                log.info("跳过表头行, 列数: {}", headerRow.getPhysicalNumberOfCells());
            }
            
            int sortOrder = 0;
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                int rowNum = row.getRowNum() + 1; // 行号从1开始
                log.info("开始解析第 {} 行", rowNum);
                
                TaskPoint point = new TaskPoint();
                
                Cell nameCell = row.getCell(0);
                if (nameCell != null) {
                    String name = getCellValueAsString(nameCell);
                    point.setName(name);
                    log.info("第 {} 行 - 点位名称: {}", rowNum, name);
                } else {
                    log.warn("第 {} 行 - 点位名称为空", rowNum);
                }
                
                Cell addressCell = row.getCell(1);
                if (addressCell != null) {
                    String address = getCellValueAsString(addressCell);
                    point.setAddress(address);
                    log.info("第 {} 行 - 点位地址: {}", rowNum, address);
                } else {
                    log.warn("第 {} 行 - 点位地址为空", rowNum);
                }

                Cell devIdCell = row.getCell(2);
                if (devIdCell != null) {
                    String devId = getCellValueAsString(devIdCell);
                    point.setDevId(devId);
                    log.info("第 {} 行 - 设备ID: {}", rowNum, devId);
                }
                
                Cell nodeIdCell = row.getCell(3);
                if (nodeIdCell != null) {
                    String nodeId = getCellValueAsString(nodeIdCell);
                    point.setNodeId(nodeId);
                    log.info("第 {} 行 - 点位ID: {}", rowNum, nodeId);
                }
                
                // 读取数据类型和位数（用于Modbus）
                Cell dataTypeCell = row.getCell(4);
                if (dataTypeCell != null) {
                    String dataType = getCellValueAsString(dataTypeCell);
                    if (dataType != null && !dataType.isEmpty()) {
                        point.setDataType(dataType.toLowerCase());
                        log.info("第 {} 行 - 数据类型: {}", rowNum, dataType);
                    } else {
                        log.warn("第 {} 行 - 数据类型为空", rowNum);
                    }
                } else {
                    log.warn("第 {} 行 - 未找到数据类型列", rowNum);
                }
                
                Cell bitLengthCell = row.getCell(5);
                if (bitLengthCell != null) {
                    String bitLengthStr = getCellValueAsString(bitLengthCell);
                    if (bitLengthStr != null && !bitLengthStr.isEmpty()) {
                        try {
                            int bitLength = Integer.parseInt(bitLengthStr);
                            point.setBitLength(bitLength);
                            log.info("第 {} 行 - 位数: {}", rowNum, bitLength);
                        } catch (NumberFormatException e) {
                            log.error("第 {} 行 - 位数解析失败: {}", rowNum, bitLengthStr, e);
                        }
                    } else {
                        log.warn("第 {} 行 - 位数为空", rowNum);
                    }
                } else {
                    log.warn("第 {} 行 - 未找到位列", rowNum);
                }
                
                // 读取比例系数（可能在第5或第6列）
                Cell scaleFactorCell = row.getCell(6);
                if (scaleFactorCell != null) {
                    String scaleFactorStr = getCellValueAsString(scaleFactorCell);
                    if (scaleFactorStr != null && !scaleFactorStr.isEmpty()) {
                        try {
                            double scaleFactor = Double.parseDouble(scaleFactorStr);
                            point.setScaleFactor(scaleFactor);
                            log.info("第 {} 行 - 比例系数: {}", rowNum, scaleFactor);
                        } catch (NumberFormatException e) {
                            log.error("第 {} 行 - 比例系数解析失败: {}", rowNum, scaleFactorStr, e);
                        }
                    } else {
                        log.warn("第 {} 行 - 比例系数为空", rowNum);
                    }
                } else {
                    // 如果第6列没有，尝试第4列（用于OPC UA）
                    scaleFactorCell = row.getCell(4);
                    if (scaleFactorCell != null) {
                        String scaleFactorStr = getCellValueAsString(scaleFactorCell);
                        if (scaleFactorStr != null && !scaleFactorStr.isEmpty()) {
                            try {
                                // 如果第4列不是数据类型，则认为是比例系数
                                if (!"int".equals(scaleFactorStr.toLowerCase()) && 
                                    !"uint".equals(scaleFactorStr.toLowerCase()) && 
                                    !"float".equals(scaleFactorStr.toLowerCase())) {
                                    double scaleFactor = Double.parseDouble(scaleFactorStr);
                                    point.setScaleFactor(scaleFactor);
                                    log.info("第 {} 行 - 从第4列解析比例系数: {}", rowNum, scaleFactor);
                                } else {
                                    log.info("第 {} 行 - 第4列是数据类型，不是比例系数", rowNum);
                                }
                            } catch (NumberFormatException e) {
                                log.error("第 {} 行 - 从第4列解析比例系数失败: {}", rowNum, scaleFactorStr, e);
                            }
                        } else {
                            log.warn("第 {} 行 - 第4列比例系数为空", rowNum);
                        }
                    } else {
                        log.warn("第 {} 行 - 未找到比例系数列", rowNum);
                    }
                }
                
                point.setSortOrder(sortOrder++);
                
                if (point.getName() != null && !point.getName().trim().isEmpty() &&
                    point.getAddress() != null && !point.getAddress().trim().isEmpty()) {
                    points.add(point);
                    log.info("第 {} 行 - 点位有效，添加到列表", rowNum);
                } else {
                    log.warn("第 {} 行 - 点位无效，跳过: 名称={}, 地址={}", 
                        rowNum, point.getName(), point.getAddress());
                }
            }
        } catch (Exception e) {
            log.error("解析Excel文件失败: {}", file.getOriginalFilename(), e);
            throw e;
        }
        
        log.info("从 Excel 导入 {} 个点位", points.size());
        return points;
    }

    /**
     * 保存多个点位
     * 
     * @param points 点位列表
     * @return 保存后的点位列表
     */
    public List<TaskPoint> savePoints(List<TaskPoint> points) {
        if (points == null || points.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<TaskPoint> savedPoints = taskPointRepository.saveAll(points);
        log.info("保存了 {} 个点位", savedPoints.size());
        return savedPoints;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        // 处理数字类型，保留小数
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
                    // 计算公式结果
                    FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cellValue = evaluator.evaluate(cell);
                    return getCellValueFromCellValue(cellValue);
                case BLANK:
                    return null;
                default:
                    return null;
            }
        } catch (Exception e) {
            log.error("获取单元格值失败: {}", e.getMessage());
            return null;
        }
    }
    
    private String getCellValueFromCellValue(CellValue cellValue) {
        if (cellValue == null) {
            return null;
        }
        
        switch (cellValue.getCellType()) {
            case STRING:
                return cellValue.getStringValue().trim();
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
                return null;
        }
    }
}
