package org.example.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AttendanceMismatch {
    private String name;
    private String projectName;
    private BigDecimal standardDays;   // 标准人天
    private BigDecimal attendanceDays; // 考勤有效天数
}
