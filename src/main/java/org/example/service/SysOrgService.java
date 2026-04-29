package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.SysOrg;
import org.example.mapper.SysOrgMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysOrgService {

    private final SysOrgMapper sysOrgMapper;

    public List<SysOrg> listAll(String orgName) {
        return sysOrgMapper.listAll(orgName);
    }

    public void add(SysOrg org) {
        if (org.getStatus() == null) org.setStatus(1);
        if (org.getParentId() == null) org.setParentId(0L);
        if (org.getSort() == null) org.setSort(0);
        sysOrgMapper.insert(org);
    }

    public void update(SysOrg org) {
        sysOrgMapper.update(org);
    }

    public void delete(Long id) {
        sysOrgMapper.deleteById(id);
    }
}
