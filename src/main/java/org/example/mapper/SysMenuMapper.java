package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.SysMenu;

import java.util.List;

@Mapper
public interface SysMenuMapper {
    List<SysMenu> listAll();
    List<SysMenu> findByRoleId(@Param("roleId") Long roleId);
    List<String> findMenuKeysByUserId(@Param("userId") Long userId);
    void deleteByRoleId(@Param("roleId") Long roleId);
    void insertRoleMenus(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);
}
