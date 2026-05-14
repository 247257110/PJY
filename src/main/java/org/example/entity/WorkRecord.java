package org.example.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class WorkRecord {
    private Long id;
    private String companyName;
    private String name;
    private String projectName;
    private String orderNo;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    private BigDecimal actualDays;
    private BigDecimal standardDays;
    private BigDecimal attendanceDays;   // 考勤有效天数
    private String workContent;
    private String sourceFile;
    private Long orgId;
    private String orgName;
    private Integer attendanceVerified; // null=未校对, 1=通过, 0=不通过
    private Integer signedDays;         // 仅用于前端预览，不入库
    private LocalDateTime createdAt;
}
