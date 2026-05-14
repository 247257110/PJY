package org.example.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.example.entity.Project;
import org.example.mapper.ProjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;

    public PageInfo<Project> list(Long orgId, String projectName, int page, int size) {
        PageHelper.startPage(page, size);
        return new PageInfo<>(projectMapper.list(orgId, projectName));
    }

    public Project getById(Long id) {
        return projectMapper.findById(id);
    }

    public void add(Project project) {
        if (project.getOrderStatus() == null || project.getOrderStatus().isBlank()) {
            project.setOrderStatus("验收未核对");
        }
        projectMapper.insert(project);
    }

    public void update(Project project) {
        projectMapper.update(project);
    }

    public void delete(Long id) {
        projectMapper.deleteById(id);
    }

    public List<Project> listByOrg(Long orgId) {
        return projectMapper.listByOrg(orgId);
    }
}
