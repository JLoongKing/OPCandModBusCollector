package com.example.opcua.service;

import com.example.opcua.entity.TaskPoint;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

    public List<TaskPoint> importFromExcel(MultipartFile file) throws IOException {
        List<TaskPoint> points = new ArrayList<>();
        
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }
            
            int sortOrder = 0;
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                
                TaskPoint point = new TaskPoint();
                
                Cell nameCell = row.getCell(0);
                if (nameCell != null) {
                    point.setName(getCellValueAsString(nameCell));
                }
                
                Cell addressCell = row.getCell(1);
                if (addressCell != null) {
                    point.setAddress(getCellValueAsString(addressCell));
                }
                
                Cell devIdCell = row.getCell(2);
                if (devIdCell != null) {
                    point.setDevId(getCellValueAsString(devIdCell));
                }
                
                Cell nodeIdCell = row.getCell(3);
                if (nodeIdCell != null) {
                    point.setNodeId(getCellValueAsString(nodeIdCell));
                }
                
                Cell scaleFactorCell = row.getCell(4);
                if (scaleFactorCell != null) {
                    String scaleFactorStr = getCellValueAsString(scaleFactorCell);
                    if (scaleFactorStr != null && !scaleFactorStr.isEmpty()) {
                        try {
                            point.setScaleFactor(Double.parseDouble(scaleFactorStr));
                        } catch (NumberFormatException e) {
                            // 忽略解析失败
                        }
                    }
                }
                
                point.setSortOrder(sortOrder++);
                
                if (point.getName() != null && !point.getName().trim().isEmpty() &&
                    point.getAddress() != null && !point.getAddress().trim().isEmpty()) {
                    points.add(point);
                }
            }
        }
        
        log.info("从 Excel 导入 {} 个点位", points.size());
        return points;
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
