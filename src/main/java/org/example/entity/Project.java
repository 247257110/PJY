package org.example.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Project {
    private Long id;
    private Long orgId;
    private String companyName;
    private String orderNo;
    private String projectNo;
    private String projectName;
    private BigDecimal orderPersonMonths;
    private BigDecimal orderAmount;
    private LocalDate orderStartDate;
    private LocalDate orderEndDate;
    private String bankResponsible;
    private String companyResponsible;
    private String orderStatus;
    private LocalDateTime createdAt;
}
