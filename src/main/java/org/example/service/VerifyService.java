package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.*;
import org.example.mapper.AttendanceRecordMapper;
import org.example.mapper.TempAttendanceRecordMapper;
import org.example.mapper.TempRecordMapper;
import org.example.mapper.WorkRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VerifyService {

    private final TempRecordMapper tempRecordMapper;
    private final WorkRecordMapper workRecordMapper;
    private final TempAttendanceRecordMapper tempAttendanceRecordMapper;
    private final AttendanceRecordMapper attendanceRecordMapper;

    public List<ConflictResult> check(String batchId) {
        List<TempRecord> tempList = tempRecordMapper.listByBatchId(batchId);
        List<ConflictResult> conflicts = new ArrayList<>();

        Map<String, List<TempRecord>> tempByName = tempList.stream()
                .collect(Collectors.groupingBy(TempRecord::getName));

        for (Map.Entry<String, List<TempRecord>> entry : tempByName.entrySet()) {
            String name = entry.getKey();
            List<TempRecord> personTemp = entry.getValue();
            List<WorkRecord> baseRecords = workRecordMapper.listByName(name);

            for (int i = 0; i < personTemp.size(); i++) {
                for (int j = i + 1; j < personTemp.size(); j++) {
                    ConflictResult c = checkOverlap(
                            name,
                            personTemp.get(i).getActualStartDate(), personTemp.get(i).getActualEndDate(), personTemp.get(i).getProjectName(),
                            personTemp.get(j).getActualStartDate(), personTemp.get(j).getActualEndDate(), personTemp.get(j).getProjectName()
                    );
                    if (c != null) conflicts.add(c);
                }
            }

            for (TempRecord t : personTemp) {
                for (WorkRecord b : baseRecords) {
                    ConflictResult c = checkOverlap(
                            name,
                            t.getActualStartDate(), t.getActualEndDate(), t.getProjectName(),
                            b.getActualStartDate(), b.getActualEndDate(), b.getProjectName()
                    );
                    if (c != null) conflicts.add(c);
                }
            }
        }

        return conflicts;
    }

    private ConflictResult checkOverlap(String name,
                                         LocalDate start1, LocalDate end1, String project1,
                                         LocalDate start2, LocalDate end2, String project2) {
        if (start1 == null || end1 == null || start2 == null || end2 == null) return null;
        if (!start1.isAfter(end2) && !end1.isBefore(start2)) {
            LocalDate overlapStart = start1.isAfter(start2) ? start1 : start2;
            LocalDate overlapEnd = end1.isBefore(end2) ? end1 : end2;
            int days = (int) ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;

            ConflictResult r = new ConflictResult();
            r.setName(name);
            r.setConflictStartDate(overlapStart);
            r.setConflictEndDate(overlapEnd);
            r.setConflictDays(days);
            r.setProject1(project1);
            r.setProject2(project2);
            return r;
        }
        return null;
    }

    @Transactional
    public void confirm(String batchId) {
        // 1. temp_record → work_record
        List<TempRecord> tempList = tempRecordMapper.listByBatchId(batchId);
        List<WorkRecord> toInsert = tempList.stream().map(t -> {
            WorkRecord w = new WorkRecord();
            w.setCompanyName(t.getCompanyName());
            w.setName(t.getName());
            w.setProjectName(t.getProjectName());
            w.setActualStartDate(t.getActualStartDate());
            w.setActualEndDate(t.getActualEndDate());
            w.setActualDays(t.getActualDays());
            w.setStandardDays(t.getStandardDays());
            w.setWorkContent(t.getWorkContent());
            w.setSourceFile(t.getSourceFile());
            w.setOrgId(t.getOrgId());
            w.setOrgName(t.getOrgName());
            return w;
        }).collect(Collectors.toList());

        if (!toInsert.isEmpty()) {
            workRecordMapper.insertBatch(toInsert); // useGeneratedKeys 填充 id
        }

        // 2. 按姓名构建 name → workRecordId 映射
        Map<String, Long> nameToWorkId = toInsert.stream()
                .filter(w -> w.getName() != null && w.getId() != null)
                .collect(Collectors.toMap(
                        w -> w.getName().trim(),
                        WorkRecord::getId,
                        (a, b) -> a));

        // 3. temp_attendance_record → attendance_record
        List<TempAttendanceRecord> tempAtts = tempAttendanceRecordMapper.listByBatchId(batchId);
        if (!tempAtts.isEmpty()) {
            List<AttendanceRecord> attendances = tempAtts.stream().map(ta -> {
                AttendanceRecord ar = new AttendanceRecord();
                ar.setName(ta.getName());
                ar.setProjectName(ta.getProjectName());
                ar.setCheckDate(ta.getCheckDate());
                ar.setMorning(ta.getMorning());
                ar.setAfternoon(ta.getAfternoon());
                ar.setSourceFile(ta.getSourceFile());
                if (ta.getName() != null) {
                    ar.setWorkRecordId(nameToWorkId.get(ta.getName().trim()));
                }
                return ar;
            }).filter(ar -> ar.getWorkRecordId() != null).collect(Collectors.toList());

            if (!attendances.isEmpty()) {
                attendanceRecordMapper.insertBatch(attendances);
            }
        }

        // 4. 清理临时表
        tempAttendanceRecordMapper.deleteByBatchId(batchId);
        tempRecordMapper.deleteByBatchId(batchId);
    }

    public void cancel(String batchId) {
        tempAttendanceRecordMapper.deleteByBatchId(batchId);
        tempRecordMapper.deleteByBatchId(batchId);
    }
}
