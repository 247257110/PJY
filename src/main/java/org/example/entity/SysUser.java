package org.example.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SysUser {
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String email;
    private String phone;
    private Integer status;
    private Long orgId;
    private LocalDateTime createdAt;
    private List<SysRole> roles;
}
