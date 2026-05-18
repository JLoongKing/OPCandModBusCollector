package com.example.opcua.service;

import com.example.opcua.entity.DataRecord;
import com.example.opcua.repository.DataRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DataRecordService {

    @Autowired
    private DataRecordRepository dataRecordRepository;

    public DataRecord saveRecord(DataRecord record) {
        if (record.getTimestamp() == null) {
            record.setTimestamp(LocalDateTime.now());
        }
        return dataRecordRepository.save(record);
    }

    public List<DataRecord> getAllRecords() {
        return dataRecordRepository.findAll();
    }

    public List<DataRecord> getRecordsByPointName(String pointName) {
        return dataRecordRepository.findByPointNameOrderByTimestampDesc(pointName);
    }

    public List<DataRecord> getRecordsByProtocolType(String protocolType) {
        return dataRecordRepository.findByProtocolTypeOrderByTimestampDesc(protocolType);
    }

    public List<DataRecord> getRecordsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return dataRecordRepository.findByTimestampBetween(startTime, endTime);
    }

    public List<DataRecord> getPointDataByTimeRange(String pointName, LocalDateTime startTime, LocalDateTime endTime) {
        return dataRecordRepository.findByPointNameAndTimestampBetween(pointName, startTime, endTime);
    }

    public Map<String, Object> getStatistics() {
        List<DataRecord> allRecords = getAllRecords();
        Map<String, Object> stats = new java.util.HashMap<>();
        
        stats.put("totalRecords", allRecords.size());
        stats.put("pointNames", allRecords.stream()
            .map(DataRecord::getPointName)
            .distinct()
            .collect(Collectors.toList()));
        stats.put("protocolTypes", allRecords.stream()
            .map(DataRecord::getProtocolType)
            .distinct()
            .collect(Collectors.toList()));
        
        if (!allRecords.isEmpty()) {
            stats.put("latestRecord", allRecords.get(allRecords.size() - 1));
        }
        
        return stats;
    }

    public void deleteAllRecords() {
        dataRecordRepository.deleteAll();
    }
}
