//package com.example.opcua.repository;
//
//import com.example.opcua.entity.DataRecord;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Repository
//public interface DataRecordRepository extends JpaRepository<DataRecord, Long> {
//
//    List<DataRecord> findByTaskIdOrderByTimestampDesc(Long taskId);
//
//    List<DataRecord> findByPointNameOrderByTimestampDesc(String pointName);
//
//    List<DataRecord> findByProtocolTypeOrderByTimestampDesc(String protocolType);
//
//    @Query("SELECT d FROM DataRecord d WHERE d.timestamp BETWEEN :startTime AND :endTime ORDER BY d.timestamp DESC")
//    List<DataRecord> findByTimestampBetween(@Param("startTime") LocalDateTime startTime,
//                                             @Param("endTime") LocalDateTime endTime);
//
//    @Query("SELECT d FROM DataRecord d WHERE d.pointName = :pointName AND d.timestamp BETWEEN :startTime AND :endTime ORDER BY d.timestamp ASC")
//    List<DataRecord> findByPointNameAndTimestampBetween(@Param("pointName") String pointName,
//                                                          @Param("startTime") LocalDateTime startTime,
//                                                          @Param("endTime") LocalDateTime endTime);
//
//    @Query("SELECT d FROM DataRecord d WHERE d.taskId = :taskId AND d.timestamp BETWEEN :startTime AND :endTime ORDER BY d.timestamp ASC")
//    List<DataRecord> findByTaskIdAndTimestampBetween(@Param("taskId") Long taskId,
//                                                       @Param("startTime") LocalDateTime startTime,
//                                                       @Param("endTime") LocalDateTime endTime);
//
//    @Query("SELECT d FROM DataRecord d WHERE d.taskId = :taskId AND d.timestamp BETWEEN :startTime AND :endTime ORDER BY d.timestamp DESC")
//    List<DataRecord> findByTaskIdAndTimestampBetweenOrderByTimestampDesc(@Param("taskId") Long taskId,
//                                                       @Param("startTime") LocalDateTime startTime,
//                                                       @Param("endTime") LocalDateTime endTime);
//
//    void deleteByTaskId(Long taskId);
//}