package org.example.entity;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ConflictResult {
    private String name;
    private LocalDate conflictStartDate;
    private LocalDate conflictEndDate;
    private int conflictDays;
    private String project1;
    private String project2;
}
