package org.example.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SysRole {
    private Long id;
    private String roleName;
    private String roleCode;
    private String remark;
    private LocalDateTime createdAt;
}
