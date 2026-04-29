package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.WorkRecord;
import java.util.List;

@Mapper
public interface WorkRecordMapper {
    List<WorkRecord> list(@Param("name") String name,
                          @Param("companyName") String companyName,
                          @Param("projectName") String projectName);

    void insertBatch(@Param("list") List<WorkRecord> list);

    void deleteById(@Param("id") Long id);

    List<WorkRecord> listByName(@Param("name") String name);

    WorkRecord findById(@Param("id") Long id);

    void updateAttendanceVerified(@Param("id") Long id, @Param("val") Integer val);
}
