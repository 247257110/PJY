package org.example.entity;

import lombok.Data;

@Data
public class SysMenu {
    private Long id;
    private String menuName;
    private String menuKey;
    private String path;
    private String icon;
    private Integer sort;
}
