package org.example.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TempRecord {
    private Long id;
    private String batchId;
    private String companyName;
    private String name;
    private String projectName;
    private String orderNo;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    private BigDecimal actualDays;
    private BigDecimal standardDays;
    private BigDecimal attendanceDays;  // 考勤有效天数
    private String workContent;
    private String sourceFile;
    private Long orgId;
    private String orgName;
    private LocalDateTime createdAt;
}
