package org.example.controller;

import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.example.config.DataPermissionHelper;
import org.example.entity.Project;
import org.example.entity.SysUser;
import org.example.service.ProjectService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sys/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final DataPermissionHelper dataPermissionHelper;

    @GetMapping("/list")
    public Map<String, Object> list(
            Authentication auth,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String projectName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!dataPermissionHelper.isAdmin(auth)) {
            SysUser user = dataPermissionHelper.currentUser(auth);
            orgId = user.getOrgId();
        }
        PageInfo<Project> pageInfo = projectService.list(orgId, projectName, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", pageInfo);
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", projectService.getById(id));
        return result;
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody Project project) {
        projectService.add(project);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "添加成功");
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Project project) {
        project.setId(id);
        projectService.update(project);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "更新成功");
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        projectService.delete(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "删除成功");
        return result;
    }

    @GetMapping("/by-org")
    public Map<String, Object> listByOrg(@RequestParam Long orgId) {
        List<Project> list = projectService.listByOrg(orgId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", list);
        return result;
    }
}
