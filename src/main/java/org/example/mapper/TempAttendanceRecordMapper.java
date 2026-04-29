package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.TempAttendanceRecord;

import java.util.List;

@Mapper
public interface TempAttendanceRecordMapper {
    void insertBatch(@Param("list") List<TempAttendanceRecord> list);
    List<TempAttendanceRecord> listByBatchId(String batchId);
    void deleteByBatchId(String batchId);
}
