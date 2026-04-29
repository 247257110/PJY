package org.example.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SysOrg {
    private Long id;
    private String orgName;
    private String orgCode;
    private Long parentId;
    private Integer sort;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
}
