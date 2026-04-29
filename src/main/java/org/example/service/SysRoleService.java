package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.SysRole;
import org.example.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper sysRoleMapper;

    public List<SysRole> listAll() {
        return sysRoleMapper.listAll();
    }

    public void add(SysRole role) {
        sysRoleMapper.insert(role);
    }

    public void update(SysRole role) {
        sysRoleMapper.update(role);
    }

    public void delete(Long id) {
        sysRoleMapper.deleteById(id);
    }
}
