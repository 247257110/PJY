package org.example.entity;

import lombok.Data;
import java.util.List;

@Data
public class ParseResult {
    private List<WorkRecord> workRecords;
    private List<AttendanceRecord> attendances;
}
