package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.SysRole;
import java.util.List;

@Mapper
public interface SysRoleMapper {
    List<SysRole> listAll();
    SysRole findById(@Param("id") Long id);
    void insert(SysRole role);
    void update(SysRole role);
    void deleteById(@Param("id") Long id);
    List<SysRole> findByUserId(@Param("userId") Long userId);
}
