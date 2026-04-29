package org.example.config;

import lombok.RequiredArgsConstructor;
import org.example.entity.SysOrg;
import org.example.entity.SysUser;
import org.example.mapper.SysOrgMapper;
import org.example.mapper.SysUserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 数据权限辅助工具
 * <p>
 * 统一封装"当前用户是否 ADMIN"和"当前用户归属机构"两个常用判断，
 * 供各 Controller 注入使用，避免重复查库逻辑。
 */
@Component
@RequiredArgsConstructor
public class DataPermissionHelper {

    private final SysUserMapper sysUserMapper;
    private final SysOrgMapper sysOrgMapper;

    /**
     * 判断当前登录用户是否拥有 ADMIN 角色。
     *
     * @param auth Spring Security Authentication（由 Controller 方法参数注入）
     * @return true = ADMIN；false = 普通用户
     */
    public boolean isAdmin(Authentication auth) {
        SysUser user = sysUserMapper.findByUsername(auth.getName());
        return user.getRoles() != null &&
                user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getRoleCode()));
    }

    /**
     * 获取当前登录用户的归属机构。
     * <p>
     * ADMIN 不限定机构，返回 null；
     * 普通用户若未绑定机构，也返回 null。
     *
     * @param auth Spring Security Authentication
     * @return 归属 SysOrg，或 null（无限制 / 未绑定）
     */
    public SysOrg currentOrg(Authentication auth) {
        SysUser user = sysUserMapper.findByUsername(auth.getName());
        if (user.getOrgId() == null) return null;
        return sysOrgMapper.findById(user.getOrgId());
    }

    /**
     * 获取当前登录用户实体（含 orgId 等完整信息）。
     *
     * @param auth Spring Security Authentication
     * @return SysUser
     */
    public SysUser currentUser(Authentication auth) {
        return sysUserMapper.findByUsername(auth.getName());
    }
}
