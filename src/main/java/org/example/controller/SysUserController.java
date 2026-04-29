package org.example.controller;

import com.github.pagehelper.PageInfo;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.config.DataPermissionHelper;
import org.example.entity.SysUser;
import org.example.service.SysUserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sys/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;
    private final DataPermissionHelper dataPermissionHelper;

    @GetMapping("/list")
    public Map<String, Object> list(
            Authentication auth,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 数据权限：非 admin 只能查看同机构用户
        Long filterOrgId = null;
        if (!dataPermissionHelper.isAdmin(auth)) {
            SysUser me = dataPermissionHelper.currentUser(auth);
            filterOrgId = me.getOrgId();
        }
        PageInfo<SysUser> pageInfo = sysUserService.list(username, realName, filterOrgId, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", pageInfo);
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", sysUserService.getById(id));
        return result;
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody UserRequest req) {
        sysUserService.add(req.toUser(), req.getRoleIds());
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "添加成功");
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody UserRequest req) {
        SysUser user = req.toUser();
        user.setId(id);
        sysUserService.update(user, req.getRoleIds());
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "更新成功");
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "删除成功");
        return result;
    }

    @Data
    static class UserRequest {
        private String username;
        private String password;
        private String realName;
        private String email;
        private String phone;
        private Integer status;
        private Long orgId;
        private List<Long> roleIds;

        SysUser toUser() {
            SysUser u = new SysUser();
            u.setUsername(username);
            u.setPassword(password);
            u.setRealName(realName);
            u.setEmail(email);
            u.setPhone(phone);
            u.setStatus(status);
            u.setOrgId(orgId);
            return u;
        }
    }
}
