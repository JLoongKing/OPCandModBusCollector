package com.example.opcua.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public class ExcelParseTest {

    private static final Logger log = LoggerFactory.getLogger(ExcelParseTest.class);

    @Test
    public void testModbusExcelParse() {
        String filePath = "c:\\Users\\WX\\Desktop\\opcua\\OPC_UA_点位导入模板.xlsx";
        
        log.info("开始测试Excel解析: {}", filePath);
        
        try (FileInputStream inputStream = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            log.info("工作表名称: {}, 行数: {}", sheet.getSheetName(), sheet.getPhysicalNumberOfRows());
            
            Iterator<Row> rowIterator = sheet.iterator();
            
            // 读取表头
            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                log.info("表头行号: {}, 列数: {}", headerRow.getRowNum(), headerRow.getPhysicalNumberOfCells());
                
                for (int i = 0; i < headerRow.getPhysicalNumberOfCells(); i++) {
                    Cell cell = headerRow.getCell(i);
                    String value = getCellValueAsString(cell);
                    log.info("第 {} 列表头: {}", i, value);
                }
            }
            
            // 读取数据行
            int rowCount = 0;
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                rowCount++;
                log.info("\n开始解析第 {} 行", row.getRowNum() + 1);
                
                for (int i = 0; i < row.getPhysicalNumberOfCells(); i++) {
                    Cell cell = row.getCell(i);
                    String value = getCellValueAsString(cell);
                    log.info("第 {} 列值: {}", i, value);
                }
            }
            
            log.info("解析完成，共解析 {} 行数据", rowCount);
            
        } catch (Exception e) {
            log.error("Excel解析失败", e);
        }
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "null";
        }
        
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
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
                    return "blank";
                default:
                    return "unknown:" + cell.getCellType();
            }
        } catch (Exception e) {
            log.error("获取单元格值失败", e);
            return "error:" + e.getMessage();
        }
    }
    
    private String getCellValueFromCellValue(CellValue cellValue) {
        if (cellValue == null) {
            return "null";
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
                return "unknown:" + cellValue.getCellType();
        }
    }
}