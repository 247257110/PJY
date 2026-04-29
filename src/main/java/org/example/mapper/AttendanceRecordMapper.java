package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.AttendanceRecord;
import java.util.List;

@Mapper
public interface AttendanceRecordMapper {
    void insertBatch(@Param("list") List<AttendanceRecord> list);
    List<AttendanceRecord> listByWorkRecordId(@Param("workRecordId") Long workRecordId);
    void deleteByWorkRecordId(@Param("workRecordId") Long workRecordId);
}
