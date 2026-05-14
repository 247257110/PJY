package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.config.DataPermissionHelper;
import org.example.entity.*;
import org.example.mapper.TempAttendanceRecordMapper;
import org.example.mapper.TempRecordMapper;
import org.example.service.AiParseService;
import org.example.service.PdfParseService;
import org.example.service.VerifyService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
public class VerifyController {

    private final PdfParseService pdfParseService;
    private final AiParseService aiParseService;
    private final VerifyService verifyService;
    private final TempRecordMapper tempRecordMapper;
    private final TempAttendanceRecordMapper tempAttendanceRecordMapper;
    private final DataPermissionHelper dataPermissionHelper;

    private boolean hasPermission(Authentication auth, String batchId) {
        if (dataPermissionHelper.isAdmin(auth)) return true;
        SysUser user = dataPermissionHelper.currentUser(auth);
        if (user.getOrgId() == null) return true;
        Long batchOrgId = tempRecordMapper.findOrgIdByBatchId(batchId);
        if (batchOrgId == null) return true;
        return user.getOrgId().equals(batchOrgId);
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(Authentication auth,
                                      @RequestParam("files") List<MultipartFile> files,
                                      @RequestParam(value = "orgId", required = false) Long orgId,
                                      @RequestParam(value = "companyName", required = false) String companyName,
                                      @RequestParam(value = "projectName", required = false) String projectName,
                                      @RequestParam(value = "orderNo", required = false) String orderNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (files == null || files.isEmpty()) {
                result.put("code", 400);
                result.put("message", "请选择至少一个文件");
                return result;
            }

            // 非 admin 且未传 orgId，使用当前用户归属机构
            if (!dataPermissionHelper.isAdmin(auth) && orgId == null) {
                SysUser currentUser = dataPermissionHelper.currentUser(auth);
                orgId = currentUser.getOrgId();
            }
            // 解析机构名称
            String resolvedOrgName = null;
            if (orgId != null) {
                SysOrg org = dataPermissionHelper.currentOrg(auth);
                if (org != null && org.getId().equals(orgId)) {
                    resolvedOrgName = org.getOrgName();
                } else {
                    resolvedOrgName = companyName;
                }
            }

            String batchId = UUID.randomUUID().toString().replace("-", "");
            List<TempRecord> allRecords = new ArrayList<>();
            List<TempAttendanceRecord> allTempAtts = new ArrayList<>();

            for (MultipartFile file : files) {
                String filename = file.getOriginalFilename();
                if (filename == null) continue;
                String lower = filename.toLowerCase();

                // 使用 Kimi 解析，Excel/Word 用 pdfParseService，其余用 Kimi
                ParseResult parsed;
                if (lower.endsWith(".xlsx") || lower.endsWith(".xls")
                        || lower.endsWith(".docx") || lower.endsWith(".doc")) {
                    parsed = pdfParseService.parseFileToResult(file, filename);
                } else {
                    parsed = aiParseService.parseFileToResultKimi(file, filename);
                }

                // 将 WorkRecord 转换为 TempRecord
                for (WorkRecord w : parsed.getWorkRecords()) {
                    TempRecord t = new TempRecord();
                    t.setBatchId(batchId);
                    t.setCompanyName(w.getCompanyName());
                    t.setName(w.getName());
                    t.setProjectName(w.getProjectName());
                    t.setActualStartDate(w.getActualStartDate());
                    t.setActualEndDate(w.getActualEndDate());
                    t.setActualDays(w.getActualDays());
                    t.setStandardDays(w.getStandardDays());
                    t.setWorkContent(w.getWorkContent());
                    t.setSourceFile(filename);
                    allRecords.add(t);
                }

                // 构建 TempAttendanceRecord
                List<AttendanceRecord> attendances = parsed.getAttendances();
                if (attendances != null) {
                    for (AttendanceRecord ar : attendances) {
                        TempAttendanceRecord ta = new TempAttendanceRecord();
                        ta.setBatchId(batchId);
                        ta.setName(ar.getName());
                        ta.setProjectName(ar.getProjectName());
                        ta.setCheckDate(ar.getCheckDate());
                        ta.setMorning(ar.getMorning());
                        ta.setAfternoon(ar.getAfternoon());
                        ta.setSourceFile(ar.getSourceFile() != null ? ar.getSourceFile() : filename);
                        allTempAtts.add(ta);
                    }
                }
            }

            // 将机构信息写入每条记录
            if (orgId != null) {
                final Long orgIdFinal = orgId;
                final String orgNameFinal = resolvedOrgName;
                allRecords.forEach(r -> { r.setOrgId(orgIdFinal); r.setOrgName(orgNameFinal); r.setCompanyName(orgNameFinal); });
            }
            // 将项目信息写入每条记录
            if (projectName != null && !projectName.isBlank()) {
                allRecords.forEach(r -> r.setProjectName(projectName));
            }
            if (orderNo != null && !orderNo.isBlank()) {
                allRecords.forEach(r -> r.setOrderNo(orderNo));
            }

            // 插入 TempRecord（useGeneratedKeys 填充 id）
            if (!allRecords.isEmpty()) {
                tempRecordMapper.insertBatch(allRecords);
            }

            // 按姓名构建 name → tempRecordId 映射
            Map<String, Long> nameToTempId = allRecords.stream()
                    .filter(r -> r.getName() != null && r.getId() != null)
                    .collect(Collectors.toMap(
                            r -> r.getName().trim(),
                            TempRecord::getId,
                            (a, b) -> a));

            // 为 TempAttendanceRecord 回填 tempRecordId
            for (TempAttendanceRecord ta : allTempAtts) {
                if (ta.getName() != null) {
                    ta.setTempRecordId(nameToTempId.get(ta.getName().trim()));
                }
            }

            if (!allTempAtts.isEmpty()) {
                tempAttendanceRecordMapper.insertBatch(allTempAtts);
            }

            // 考勤有效天数汇总（按姓名，去重统计日期）
            Map<String, java.util.Set<String>> attNameDates = new LinkedHashMap<>();
            for (TempAttendanceRecord ta : allTempAtts) {
                if (ta.getName() == null || ta.getCheckDate() == null) continue;
                if (!"有".equals(ta.getMorning()) && !"有".equals(ta.getAfternoon())) continue;
                attNameDates.computeIfAbsent(ta.getName().trim(), k -> new HashSet<>())
                        .add(ta.getCheckDate().toString());
            }
            List<Map<String, Object>> attendanceSummary = new ArrayList<>();
            for (Map.Entry<String, java.util.Set<String>> e : attNameDates.entrySet()) {
                Map<String, Object> s = new HashMap<>();
                s.put("name", e.getKey());
                s.put("attendanceDays", e.getValue().size());
                attendanceSummary.add(s);
            }

            result.put("code", 200);
            result.put("batchId", batchId);
            result.put("records", allRecords);
            result.put("attendances", allTempAtts);
            result.put("attendanceSummary", attendanceSummary);
            result.put("total", allRecords.size());
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("message", "解析失败：" + e.getMessage());
        }
        return result;
    }

    @GetMapping("/check/{batchId}")
    public Map<String, Object> check(Authentication auth, @PathVariable String batchId) {
        Map<String, Object> result = new HashMap<>();
        if (!hasPermission(auth, batchId)) {
            result.put("code", 403);
            result.put("message", "无权访问该批次");
            return result;
        }
        Map<String, Object> checkResult = verifyService.check(batchId);
        List<?> conflicts = (List<?>) checkResult.get("conflicts");
        List<?> mismatches = (List<?>) checkResult.get("attendanceMismatches");
        result.put("code", 200);
        result.put("pass", conflicts.isEmpty() && mismatches.isEmpty());
        result.put("conflicts", conflicts);
        result.put("attendanceMismatches", mismatches);
        result.put("attendanceSummary", checkResult.get("attendanceSummary"));
        return result;
    }

    @PostMapping("/confirm/{batchId}")
    public Map<String, Object> confirm(Authentication auth, @PathVariable String batchId) {
        Map<String, Object> result = new HashMap<>();
        if (!hasPermission(auth, batchId)) {
            result.put("code", 403);
            result.put("message", "无权操作该批次");
            return result;
        }
        Map<String, Object> checkResult = verifyService.check(batchId);
        List<?> conflicts = (List<?>) checkResult.get("conflicts");
        if (!conflicts.isEmpty()) {
            result.put("code", 400);
            result.put("message", "存在重复投入，不能入库");
            result.put("conflicts", conflicts);
            return result;
        }
        verifyService.confirm(batchId);
        result.put("code", 200);
        result.put("message", "已成功纳入基础库");
        return result;
    }

    @DeleteMapping("/cancel/{batchId}")
    public Map<String, Object> cancel(Authentication auth, @PathVariable String batchId) {
        Map<String, Object> result = new HashMap<>();
        if (!hasPermission(auth, batchId)) {
            result.put("code", 403);
            result.put("message", "无权操作该批次");
            return result;
        }
        verifyService.cancel(batchId);
        result.put("code", 200);
        result.put("message", "已取消");
        return result;
    }

    @GetMapping("/batches")
    public Map<String, Object> getBatches(Authentication auth) {
        Map<String, Object> result = new HashMap<>();
        boolean isAdmin = dataPermissionHelper.isAdmin(auth);
        SysUser user = dataPermissionHelper.currentUser(auth);
        Long orgId = isAdmin ? null : user.getOrgId();
        List<Map<String, Object>> batches = tempRecordMapper.selectBatchSummary(orgId, isAdmin);
        result.put("code", 200);
        result.put("data", batches);
        return result;
    }

    @GetMapping("/preview/{batchId}")
    public Map<String, Object> preview(Authentication auth, @PathVariable String batchId) {
        Map<String, Object> result = new HashMap<>();
        if (!hasPermission(auth, batchId)) {
            result.put("code", 403);
            result.put("message", "无权访问该批次");
            return result;
        }
        List<TempRecord> records = tempRecordMapper.listByBatchId(batchId);
        List<TempAttendanceRecord> atts = tempAttendanceRecordMapper.listByBatchId(batchId);

        // 考勤有效天数汇总（按姓名，去重统计）
        Map<String, java.util.Set<String>> nameDates = new LinkedHashMap<>();
        for (TempAttendanceRecord ta : atts) {
            if (ta.getName() == null || ta.getCheckDate() == null) continue;
            if (!"有".equals(ta.getMorning()) && !"有".equals(ta.getAfternoon())) continue;
            nameDates.computeIfAbsent(ta.getName().trim(), k -> new HashSet<>())
                    .add(ta.getCheckDate().toString());
        }
        List<Map<String, Object>> attendanceSummary = new ArrayList<>();
        for (Map.Entry<String, java.util.Set<String>> e : nameDates.entrySet()) {
            Map<String, Object> s = new HashMap<>();
            s.put("name", e.getKey());
            s.put("attendanceDays", e.getValue().size());
            attendanceSummary.add(s);
        }

        result.put("code", 200);
        result.put("data", records);
        result.put("attendances", atts);
        result.put("attendanceSummary", attendanceSummary);
        return result;
    }
}
