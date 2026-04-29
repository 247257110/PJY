package org.example.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.example.entity.AttendanceRecord;
import org.example.entity.WorkRecord;
import org.example.mapper.AttendanceRecordMapper;
import org.example.mapper.WorkRecordMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BaseLibService {

    private final WorkRecordMapper workRecordMapper;
    private final AttendanceRecordMapper attendanceRecordMapper;

    public PageInfo<WorkRecord> list(String name, String companyName, String projectName, int page, int size) {
        PageHelper.startPage(page, size);
        List<WorkRecord> list = workRecordMapper.list(name, companyName, projectName);
        return new PageInfo<>(list);
    }

    public void deleteById(Long id) {
        attendanceRecordMapper.deleteByWorkRecordId(id);
        workRecordMapper.deleteById(id);
    }

    public void insertBatch(List<WorkRecord> list) {
        if (list != null && !list.isEmpty()) {
            workRecordMapper.insertBatch(list);
        }
    }

    /**
     * 仅计算考勤校验结果，不写库。
     * 在每条 WorkRecord 上设置 attendanceVerified 和 signedDays。
     */
    public List<WorkRecord> verifyAttendance(List<WorkRecord> workRecords, List<AttendanceRecord> attendances) {
        Map<String, List<AttendanceRecord>> attByName = attendances == null ? Collections.emptyMap() :
                attendances.stream()
                        .filter(a -> a.getName() != null)
                        .collect(Collectors.groupingBy(a -> a.getName().trim()));
        for (WorkRecord wr : workRecords) {
            if (wr.getActualStartDate() == null || wr.getActualEndDate() == null) continue;
            String name = wr.getName() == null ? "" : wr.getName().trim();
            List<AttendanceRecord> personAtt = attByName.getOrDefault(name, Collections.emptyList());
            Set<LocalDate> workdays = getWorkdays(wr.getActualStartDate(), wr.getActualEndDate());
            long signedDays = personAtt.stream()
                    .filter(a -> a.getCheckDate() != null && workdays.contains(a.getCheckDate()))
                    .filter(a -> "有".equals(a.getMorning()) || "有".equals(a.getAfternoon()))
                    .map(AttendanceRecord::getCheckDate).distinct().count();
            wr.setSignedDays((int) signedDays);
            if (personAtt.isEmpty()) {
                wr.setAttendanceVerified(null);
            } else if (wr.getStandardDays() != null && wr.getStandardDays().compareTo(BigDecimal.ZERO) > 0) {
                wr.setAttendanceVerified(wr.getStandardDays().compareTo(BigDecimal.valueOf(signedDays)) == 0 ? 1 : 0);
            } else {
                wr.setAttendanceVerified(signedDays > 0 ? 1 : 0);
            }
        }
        return workRecords;
    }

    /**
     * 批量入库并进行考勤校对。
     * workRecords 入库后获得 id，再与 attendances 按姓名匹配，
     * 计算工作日内签到天数与 standardDays 比较，写入 attendance_verified。
     * 考勤记录无论校验结果均入库。
     */
    public void insertBatchWithVerification(List<WorkRecord> workRecords, List<AttendanceRecord> attendances) {
        if (workRecords == null || workRecords.isEmpty()) return;
        workRecordMapper.insertBatch(workRecords); // useGeneratedKeys 填充 id

        // 按姓名分组考勤记录
        Map<String, List<AttendanceRecord>> attByName = attendances == null ? Collections.emptyMap() :
                attendances.stream()
                        .filter(a -> a.getName() != null)
                        .collect(Collectors.groupingBy(a -> a.getName().trim()));

        List<AttendanceRecord> toSave = new ArrayList<>();
        for (WorkRecord wr : workRecords) {
            if (wr.getId() == null || wr.getActualStartDate() == null || wr.getActualEndDate() == null) continue;
            String name = wr.getName() == null ? "" : wr.getName().trim();
            List<AttendanceRecord> personAtt = attByName.getOrDefault(name, Collections.emptyList());

            // 工作日集合
            Set<LocalDate> workdays = getWorkdays(wr.getActualStartDate(), wr.getActualEndDate());
            // 签到天数：工作日内上午或下午"有"，按日期去重统计
            long signedDays = personAtt.stream()
                    .filter(a -> a.getCheckDate() != null && workdays.contains(a.getCheckDate()))
                    .filter(a -> "有".equals(a.getMorning()) || "有".equals(a.getAfternoon()))
                    .map(AttendanceRecord::getCheckDate)
                    .distinct()
                    .count();

            Integer verified;
            if (personAtt.isEmpty()) {
                verified = null;
            } else if (wr.getStandardDays() != null && wr.getStandardDays().compareTo(BigDecimal.ZERO) > 0) {
                verified = (wr.getStandardDays().compareTo(BigDecimal.valueOf(signedDays)) == 0) ? 1 : 0;
            } else {
                verified = signedDays > 0 ? 1 : 0;
            }
            workRecordMapper.updateAttendanceVerified(wr.getId(), verified);

            if (!personAtt.isEmpty()) {
                for (AttendanceRecord ar : personAtt) {
                    if (ar.getCheckDate() != null && workdays.contains(ar.getCheckDate())) {
                        ar.setWorkRecordId(wr.getId());
                        if (ar.getProjectName() == null) ar.setProjectName(wr.getProjectName());
                        toSave.add(ar);
                    }
                }
            }
        }
        if (!toSave.isEmpty()) {
            attendanceRecordMapper.insertBatch(toSave);
        }
    }

    /** 查询某工作记录的考勤明细，返回日期范围内每天的签到状态 */
    public Map<String, Object> getAttendanceDetail(Long workRecordId) {
        WorkRecord wr = workRecordMapper.findById(workRecordId);
        List<AttendanceRecord> records = attendanceRecordMapper.listByWorkRecordId(workRecordId);
        Set<LocalDate> signedDates = records.stream()
                .map(AttendanceRecord::getCheckDate)
                .collect(Collectors.toSet());

        List<Map<String, Object>> list = new ArrayList<>();
        long totalSigned = 0;
        if (wr != null && wr.getActualStartDate() != null && wr.getActualEndDate() != null) {
            LocalDate cur = wr.getActualStartDate();
            while (!cur.isAfter(wr.getActualEndDate())) {
                boolean isWorkday = cur.getDayOfWeek() != DayOfWeek.SATURDAY
                        && cur.getDayOfWeek() != DayOfWeek.SUNDAY;
                boolean signed = signedDates.contains(cur);
                Map<String, Object> day = new HashMap<>();
                day.put("checkDate", cur.toString());
                day.put("isWorkday", isWorkday);
                day.put("signed", signed);
                list.add(day);
                if (isWorkday && signed) totalSigned++;
                cur = cur.plusDays(1);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("totalSigned", totalSigned);
        result.put("standardDays", wr != null ? wr.getStandardDays() : null);
        return result;
    }

    private Set<LocalDate> getWorkdays(LocalDate start, LocalDate end) {
        Set<LocalDate> days = new HashSet<>();
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            if (cur.getDayOfWeek() != DayOfWeek.SATURDAY && cur.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(cur);
            }
            cur = cur.plusDays(1);
        }
        return days;
    }
}
