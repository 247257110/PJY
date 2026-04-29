package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.TempRecord;
import java.util.List;

@Mapper
public interface TempRecordMapper {
    void insertBatch(@Param("list") List<TempRecord> list);

    List<TempRecord> listByBatchId(@Param("batchId") String batchId);

    void deleteByBatchId(@Param("batchId") String batchId);

    List<TempRecord> listByName(@Param("name") String name, @Param("batchId") String batchId);

    /** 查询某批次第一条记录的 org_id（用于权限校验） */
    Long findOrgIdByBatchId(@Param("batchId") String batchId);
}
