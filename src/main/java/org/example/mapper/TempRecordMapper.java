package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.TempRecord;
import java.util.List;
import java.util.Map;

@Mapper
public interface TempRecordMapper {
    void insertBatch(@Param("list") List<TempRecord> list);

    List<TempRecord> listByBatchId(@Param("batchId") String batchId);

    void deleteByBatchId(@Param("batchId") String batchId);

    List<TempRecord> listByName(@Param("name") String name, @Param("batchId") String batchId);

    /** 查询某批次第一条记录的 org_id（用于权限校验） */
    Long findOrgIdByBatchId(@Param("batchId") String batchId);

    /** 更新考勤有效天数 */
    void updateAttendanceDays(@Param("id") Long id, @Param("attendanceDays") java.math.BigDecimal attendanceDays);

    /** 按 batch_id 分组聚合，返回历史批次摘要 */
    List<Map<String, Object>> selectBatchSummary(@Param("orgId") Long orgId, @Param("isAdmin") boolean isAdmin);
}
