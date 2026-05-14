package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.Project;

import java.util.List;

@Mapper
public interface ProjectMapper {
    List<Project> list(@Param("orgId") Long orgId, @Param("projectName") String projectName);
    Project findById(@Param("id") Long id);
    void insert(Project project);
    void update(Project project);
    void deleteById(@Param("id") Long id);
    List<Project> listByOrg(@Param("orgId") Long orgId);
}
