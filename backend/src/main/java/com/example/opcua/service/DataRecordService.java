package com.example.opcua.service;

import com.example.opcua.entity.DataRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DataRecordService {

    private final JdbcTemplate jdbcTemplate;
    private static final DateTimeFormatter TABLE_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    public DataRecordService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(DataRecord record) {
        String tableName = getTableName(record.getTimestamp());
        ensureTableExists(tableName);

        String sql = String.format(
                "INSERT INTO %s (task_id, task_name, point_name, protocol_type, address, record_value, data_type, timestamp) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                tableName);

        jdbcTemplate.update(sql,
                record.getTaskId(),
                record.getTaskName(),
                record.getPointName(),
                record.getProtocolType(),
                record.getAddress(),
                record.getValue(),
                record.getDataType(),
                record.getTimestamp());
    }

    public List<DataRecord> findByTaskIdOrderByTimestampDesc(Long taskId) {
        List<String> tableNames = getAllDataTables();
        List<DataRecord> allRecords = new ArrayList<>();

        for (String tableName : tableNames) {
            try {
                String sql = String.format(
                        "SELECT * FROM %s WHERE task_id = ? ORDER BY timestamp DESC LIMIT 1000",
                        tableName);
                List<DataRecord> records = jdbcTemplate.query(sql, new DataRecordRowMapper(), taskId);
                allRecords.addAll(records);
            } catch (Exception e) {
                log.debug("查询表 {} 失败: {}", tableName, e.getMessage());
            }
        }

        allRecords.sort((a, b) -> {
            if (a.getTimestamp() == null && b.getTimestamp() == null) return 0;
            if (a.getTimestamp() == null) return 1;
            if (b.getTimestamp() == null) return -1;
            return b.getTimestamp().compareTo(a.getTimestamp());
        });

        if (allRecords.size() > 1000) {
            return allRecords.subList(0, 1000);
        }
        return allRecords;
    }

    public List<DataRecord> findByTaskIdAndTimestampBetweenOrderByTimestampAsc(Long taskId,
                                                                                LocalDateTime startTime,
                                                                                LocalDateTime endTime) {
        List<String> tableNames = getTablesInRange(startTime, endTime);
        List<DataRecord> allRecords = new ArrayList<>();

        for (String tableName : tableNames) {
            try {
                String sql = String.format(
                        "SELECT * FROM %s WHERE task_id = ? AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp ASC",
                        tableName);
                List<DataRecord> records = jdbcTemplate.query(sql, new DataRecordRowMapper(),
                        taskId, startTime, endTime);
                allRecords.addAll(records);
            } catch (Exception e) {
                log.debug("查询表 {} 失败: {}", tableName, e.getMessage());
            }
        }

        allRecords.sort((a, b) -> {
            if (a.getTimestamp() == null && b.getTimestamp() == null) return 0;
            if (a.getTimestamp() == null) return 1;
            if (b.getTimestamp() == null) return -1;
            return a.getTimestamp().compareTo(b.getTimestamp());
        });

        return allRecords;
    }

    public List<DataRecord> findByTaskIdAndTimestampBetweenOrderByTimestampDesc(Long taskId,
                                                                                 LocalDateTime startTime,
                                                                                 LocalDateTime endTime) {
        List<String> tableNames = getTablesInRange(startTime, endTime);
        List<DataRecord> allRecords = new ArrayList<>();

        for (String tableName : tableNames) {
            try {
                String sql = String.format(
                        "SELECT * FROM %s WHERE task_id = ? AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC",
                        tableName);
                List<DataRecord> records = jdbcTemplate.query(sql, new DataRecordRowMapper(),
                        taskId, startTime, endTime);
                allRecords.addAll(records);
            } catch (Exception e) {
                log.debug("查询表 {} 失败: {}", tableName, e.getMessage());
            }
        }

        allRecords.sort((a, b) -> {
            if (a.getTimestamp() == null && b.getTimestamp() == null) return 0;
            if (a.getTimestamp() == null) return 1;
            if (b.getTimestamp() == null) return -1;
            return b.getTimestamp().compareTo(a.getTimestamp());
        });

        return allRecords;
    }

    public List<DataRecord> findByPointNameAndTimestampBetween(String pointName,
                                                                LocalDateTime startTime,
                                                                LocalDateTime endTime) {
        List<String> tableNames = getTablesInRange(startTime, endTime);
        List<DataRecord> allRecords = new ArrayList<>();

        for (String tableName : tableNames) {
            try {
                String sql = String.format(
                        "SELECT * FROM %s WHERE point_name = ? AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp ASC",
                        tableName);
                List<DataRecord> records = jdbcTemplate.query(sql, new DataRecordRowMapper(),
                        pointName, startTime, endTime);
                allRecords.addAll(records);
            } catch (Exception e) {
                log.debug("查询表 {} 失败: {}", tableName, e.getMessage());
            }
        }

        allRecords.sort((a, b) -> {
            if (a.getTimestamp() == null && b.getTimestamp() == null) return 0;
            if (a.getTimestamp() == null) return 1;
            if (b.getTimestamp() == null) return -1;
            return a.getTimestamp().compareTo(b.getTimestamp());
        });

        return allRecords;
    }

    public void deleteByTaskId(Long taskId) {
        List<String> tableNames = getAllDataTables();
        for (String tableName : tableNames) {
            try {
                String sql = String.format("DELETE FROM %s WHERE task_id = ?", tableName);
                jdbcTemplate.update(sql, taskId);
            } catch (Exception e) {
                log.debug("从表 {} 删除数据失败: {}", tableName, e.getMessage());
            }
        }
    }

    private String getTableName(LocalDateTime timestamp) {
        String suffix = timestamp.format(TABLE_SUFFIX_FORMATTER);
        return "data_record_" + suffix;
    }

    private List<String> getTablesInRange(LocalDateTime startTime, LocalDateTime endTime) {
        List<String> tables = new ArrayList<>();
        YearMonth start = YearMonth.from(startTime);
        YearMonth end = YearMonth.from(endTime);

        YearMonth current = start;
        while (!current.isAfter(end)) {
            tables.add("data_record_" + current.format(TABLE_SUFFIX_FORMATTER));
            current = current.plusMonths(1);
        }
        return tables;
    }

    private List<String> getAllDataTables() {
        List<String> tables = new ArrayList<>();
        try {
            String sql = "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_name LIKE 'data_record_%' " +
                    "ORDER BY table_name DESC";
            tables = jdbcTemplate.queryForList(sql, String.class);
        } catch (Exception e) {
            log.debug("查询数据表列表失败: {}", e.getMessage());
        }
        return tables;
    }

    private void ensureTableExists(String tableName) {
        try {
            String checkSql = "SELECT to_regclass('public." + tableName + "')";
            String result = jdbcTemplate.queryForObject(checkSql, String.class);
            if (result != null && !result.isEmpty()) {
                return;
            }
        } catch (Exception e) {
            log.debug("检查表 {} 是否存在时出错: {}", tableName, e.getMessage());
        }

        String createSql = String.format(
                "CREATE TABLE IF NOT EXISTS %s (" +
                        "id BIGSERIAL PRIMARY KEY, " +
                        "task_id BIGINT, " +
                        "task_name VARCHAR(100), " +
                        "point_name VARCHAR(100), " +
                        "protocol_type VARCHAR(20), " +
                        "address VARCHAR(255), " +
                        "record_value VARCHAR(255), " +
                        "data_type VARCHAR(50), " +
                        "timestamp TIMESTAMP" +
                        ")",
                tableName);

        jdbcTemplate.execute(createSql);

        String indexSql = String.format(
                "CREATE INDEX IF NOT EXISTS idx_%s_task_id_timestamp ON %s (task_id, timestamp)",
                tableName.replace("data_record_", ""), tableName);
        try {
            jdbcTemplate.execute(indexSql);
        } catch (Exception e) {
            log.debug("创建索引失败: {}", e.getMessage());
        }

        String pointIndexSql = String.format(
                "CREATE INDEX IF NOT EXISTS idx_%s_point_name ON %s (point_name)",
                tableName.replace("data_record_", ""), tableName);
        try {
            jdbcTemplate.execute(pointIndexSql);
        } catch (Exception e) {
            log.debug("创建索引失败: {}", e.getMessage());
        }

        log.info("已创建数据分表: {}", tableName);
    }

    private static class DataRecordRowMapper implements RowMapper<DataRecord> {
        @Override
        public DataRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            DataRecord record = new DataRecord();
            record.setId(rs.getLong("id"));
            record.setTaskId(rs.getLong("task_id"));
            record.setTaskName(rs.getString("task_name"));
            record.setPointName(rs.getString("point_name"));
            record.setProtocolType(rs.getString("protocol_type"));
            record.setAddress(rs.getString("address"));
            record.setValue(rs.getString("record_value"));
            record.setDataType(rs.getString("data_type"));
            record.setTimestamp(rs.getObject("timestamp", LocalDateTime.class));
            return record;
        }
    }
}