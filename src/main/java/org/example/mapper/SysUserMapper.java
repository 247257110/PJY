package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.SysUser;
import java.util.List;

@Mapper
public interface SysUserMapper {
    SysUser findByUsername(@Param("username") String username);
    List<SysUser> list(@Param("username") String username, @Param("realName") String realName,
                       @Param("orgId") Long orgId);
    void insert(SysUser user);
    void update(SysUser user);
    void deleteById(@Param("id") Long id);
    void deleteUserRoles(@Param("userId") Long userId);
    void insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);
    SysUser findById(@Param("id") Long id);
}
