package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.entity.SysOrg;
import org.example.service.SysOrgService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sys/org")
@RequiredArgsConstructor
public class SysOrgController {

    private final SysOrgService sysOrgService;

    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam(required = false) String orgName) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", sysOrgService.listAll(orgName));
        return result;
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody SysOrg org) {
        sysOrgService.add(org);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "添加成功");
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody SysOrg org) {
        org.setId(id);
        sysOrgService.update(org);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "更新成功");
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        sysOrgService.delete(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "删除成功");
        return result;
    }
}
