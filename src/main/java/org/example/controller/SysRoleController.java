package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.entity.SysMenu;
import org.example.entity.SysRole;
import org.example.mapper.SysMenuMapper;
import org.example.service.SysRoleService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sys/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;
    private final SysMenuMapper sysMenuMapper;

    @GetMapping("/list")
    public Map<String, Object> list() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", sysRoleService.listAll());
        return result;
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody SysRole role) {
        sysRoleService.add(role);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "添加成功");
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody SysRole role) {
        role.setId(id);
        sysRoleService.update(role);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "更新成功");
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        sysRoleService.delete(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "删除成功");
        return result;
    }

    @GetMapping("/menus/all")
    public Map<String, Object> allMenus() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", sysMenuMapper.listAll());
        return result;
    }

    @GetMapping("/{id}/menus")
    public Map<String, Object> getRoleMenus(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", sysMenuMapper.findByRoleId(id).stream()
                .map(m -> m.getId()).collect(java.util.stream.Collectors.toList()));
        return result;
    }

    @PutMapping("/{id}/menus")
    public Map<String, Object> updateRoleMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        sysMenuMapper.deleteByRoleId(id);
        if (menuIds != null && !menuIds.isEmpty()) {
            sysMenuMapper.insertRoleMenus(id, menuIds);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "权限更新成功");
        return result;
    }
}
