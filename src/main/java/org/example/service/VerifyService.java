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
import org.example.util.ChineseHolidayUtil;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VerifyService {

    private final TempRecordMapper tempRecordMapper;
    private final WorkRecordMapper workRecordMapper;
    private final TempAttendanceRecordMapper tempAttendanceRecordMapper;
    private final AttendanceRecordMapper attendanceRecordMapper;

    public Map<String, Object> check(String batchId) {
        List<TempRecord> tempList = tempRecordMapper.listByBatchId(batchId);
        List<ConflictResult> conflicts = new ArrayList<>();

        // ── 1. 重复投入校验 ──────────────────────────────────────
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

        // ── 2. 考勤天数校验 ──────────────────────────────────────
        List<TempAttendanceRecord> attList = tempAttendanceRecordMapper.listByBatchId(batchId);
        // 按姓名分组考勤记录
        Map<String, List<TempAttendanceRecord>> attByName = attList.stream()
                .filter(a -> a.getName() != null)
                .collect(Collectors.groupingBy(a -> a.getName().trim()));

        // 按姓名+项目聚合考勤结果（同一人可能有多个 TempRecord，取最大考勤天数）
        java.util.LinkedHashMap<String, AttendanceMismatch> mismatchMap = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, java.math.BigDecimal> summaryMap = new java.util.LinkedHashMap<>();

        for (TempRecord t : tempList) {
            if (t.getName() == null || t.getStandardDays() == null) continue;
            String name = t.getName().trim();

            List<TempAttendanceRecord> personAtt = attByName.getOrDefault(name, Collections.emptyList());
            long attDays;
            if (t.getActualStartDate() != null && t.getActualEndDate() != null) {
                Set<LocalDate> workdays = getWorkdays(t.getActualStartDate(), t.getActualEndDate());
                attDays = personAtt.stream()
                        .filter(a -> a.getCheckDate() != null && workdays.contains(a.getCheckDate()))
                        .filter(a -> "有".equals(a.getMorning()) || "有".equals(a.getAfternoon()))
                        .map(TempAttendanceRecord::getCheckDate)
                        .distinct()
                        .count();
            } else {
                attDays = personAtt.stream()
                        .filter(a -> "有".equals(a.getMorning()) || "有".equals(a.getAfternoon()))
                        .map(TempAttendanceRecord::getCheckDate)
                        .distinct()
                        .count();
            }

            java.math.BigDecimal attDaysBD = java.math.BigDecimal.valueOf(attDays);
            // 回写 attendance_days 到 temp_record
            tempRecordMapper.updateAttendanceDays(t.getId(), attDaysBD);

            // 汇总每人考勤有效天数（取最大值）
            summaryMap.merge(name, attDaysBD,
                    (old, cur) -> old.compareTo(cur) >= 0 ? old : cur);

            // 按姓名+项目聚合不一致明细
            String key = name + "|" + (t.getProjectName() != null ? t.getProjectName() : "");
            AttendanceMismatch existing = mismatchMap.get(key);
            if (existing == null) {
                if (t.getStandardDays().compareTo(attDaysBD) != 0) {
                    AttendanceMismatch m = new AttendanceMismatch();
                    m.setName(name);
                    m.setProjectName(t.getProjectName());
                    m.setStandardDays(t.getStandardDays());
                    m.setAttendanceDays(attDaysBD);
                    mismatchMap.put(key, m);
                }
            } else {
                // 同一姓名+项目的多条记录，取最大的考勤天数
                if (attDaysBD.compareTo(existing.getAttendanceDays()) > 0) {
                    existing.setAttendanceDays(attDaysBD);
                    existing.setStandardDays(t.getStandardDays());
                }
            }
        }

        List<AttendanceMismatch> mismatches = new ArrayList<>(mismatchMap.values());

        // 构造考勤有效天数汇总
        List<Map<String, Object>> attendanceSummary = new ArrayList<>();
        for (Map.Entry<String, java.math.BigDecimal> e : summaryMap.entrySet()) {
            Map<String, Object> s = new HashMap<>();
            s.put("name", e.getKey());
            s.put("attendanceDays", e.getValue());
            attendanceSummary.add(s);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("conflicts", conflicts);
        result.put("attendanceMismatches", mismatches);
        result.put("attendanceSummary", attendanceSummary);
        return result;
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
        // 1. temp_record → work_record（含考勤校对结果）
        List<TempRecord> tempList = tempRecordMapper.listByBatchId(batchId);
        List<WorkRecord> toInsert = tempList.stream().map(t -> {
            WorkRecord w = new WorkRecord();
            w.setCompanyName(t.getCompanyName());
            w.setName(t.getName());
            w.setProjectName(t.getProjectName());
            w.setOrderNo(t.getOrderNo());
            w.setActualStartDate(t.getActualStartDate());
            w.setActualEndDate(t.getActualEndDate());
            w.setActualDays(t.getActualDays());
            w.setStandardDays(t.getStandardDays());
            w.setWorkContent(t.getWorkContent());
            w.setSourceFile(t.getSourceFile());
            w.setOrgId(t.getOrgId());
            w.setOrgName(t.getOrgName());
            w.setAttendanceDays(t.getAttendanceDays());
            // 考勤校对：attendance_days 已在 check() 时回写，null 视为未校对
            if (t.getAttendanceDays() != null && t.getStandardDays() != null) {
                w.setAttendanceVerified(t.getStandardDays().compareTo(t.getAttendanceDays()) == 0 ? 1 : 0);
            }
            return w;
        }).collect(Collectors.toList());

        if (!toInsert.isEmpty()) {
            workRecordMapper.insertBatch(toInsert);
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
            }).filter(ar -> ar.getWorkRecordId() != null
                    // 过滤掉上午和下午都为"无"的记录，避免写入无效考勤数据
                    && ("有".equals(ar.getMorning()) || "有".equals(ar.getAfternoon()))
            ).collect(Collectors.toList());

            if (!attendances.isEmpty()) {
                attendanceRecordMapper.insertBatch(attendances);
            }
        }

        // 4. 清理临时表
        tempAttendanceRecordMapper.deleteByBatchId(batchId);
        tempRecordMapper.deleteByBatchId(batchId);
    }

    private Set<LocalDate> getWorkdays(LocalDate start, LocalDate end) {
        Set<LocalDate> days = new HashSet<>();
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            if (ChineseHolidayUtil.isWorkday(cur)) {
                days.add(cur);
            }
            cur = cur.plusDays(1);
        }
        return days;
    }

    public void cancel(String batchId) {
        tempAttendanceRecordMapper.deleteByBatchId(batchId);
        tempRecordMapper.deleteByBatchId(batchId);
    }
}
