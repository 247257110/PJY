package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.SysOrg;
import java.util.List;

@Mapper
public interface SysOrgMapper {
    List<SysOrg> listAll(@Param("orgName") String orgName);
    SysOrg findById(@Param("id") Long id);
    void insert(SysOrg org);
    void update(SysOrg org);
    void deleteById(@Param("id") Long id);
}
