package org.example.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.example.entity.SysUser;
import org.example.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public PageInfo<SysUser> list(String username, String realName, Long orgId, int page, int size) {
        PageHelper.startPage(page, size);
        return new PageInfo<>(sysUserMapper.list(username, realName, orgId));
    }

    @Transactional
    public void add(SysUser user, List<Long> roleIds) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(user.getStatus() == null ? 1 : user.getStatus());
        sysUserMapper.insert(user);
        if (roleIds != null && !roleIds.isEmpty()) {
            sysUserMapper.insertUserRoles(user.getId(), roleIds);
        }
    }

    @Transactional
    public void update(SysUser user, List<Long> roleIds) {
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        sysUserMapper.update(user);
        if (roleIds != null) {
            sysUserMapper.deleteUserRoles(user.getId());
            if (!roleIds.isEmpty()) {
                sysUserMapper.insertUserRoles(user.getId(), roleIds);
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        sysUserMapper.deleteUserRoles(id);
        sysUserMapper.deleteById(id);
    }

    public SysUser getById(Long id) {
        return sysUserMapper.findById(id);
    }
}
