package org.example.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AttendanceRecord {
    private Long id;
    private Long workRecordId;
    private String projectName;
    private String name;
    private LocalDate checkDate;
    private String morning;   // 上午: 有/无
    private String afternoon; // 下午: 有/无
    private String sourceFile;
    private LocalDateTime createdAt;
}
