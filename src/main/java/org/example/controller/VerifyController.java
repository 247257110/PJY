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
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "orgId", required = false) Long orgId,
                                      @RequestParam(value = "companyName", required = false) String companyName) {
        Map<String, Object> result = new HashMap<>();
        try {
            String filename = file.getOriginalFilename();
            if (filename == null) {
                result.put("code", 400);
                result.put("message", "文件名不能为空");
                return result;
            }
            String lower = filename.toLowerCase();
            String batchId = UUID.randomUUID().toString().replace("-", "");

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

            // 使用 Kimi 解析，Excel/Word 用 pdfParseService，其余用 Kimi
            ParseResult parsed;
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")
                    || lower.endsWith(".docx") || lower.endsWith(".doc")) {
                parsed = pdfParseService.parseFileToResult(file, filename);
            } else {
                parsed = aiParseService.parseFileToResultKimi(file, filename);
            }

            // 将 WorkRecord 转换为 TempRecord
            List<TempRecord> records = new ArrayList<>();
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
                records.add(t);
            }

            // 将机构信息写入每条记录
            if (orgId != null) {
                final Long orgIdFinal = orgId;
                final String orgNameFinal = resolvedOrgName;
                records.forEach(r -> { r.setOrgId(orgIdFinal); r.setOrgName(orgNameFinal); });
            }

            // 插入 TempRecord（useGeneratedKeys 填充 id）
            if (!records.isEmpty()) {
                tempRecordMapper.insertBatch(records);
            }

            // 按姓名构建 name → tempRecordId 映射
            Map<String, Long> nameToTempId = records.stream()
                    .filter(r -> r.getName() != null && r.getId() != null)
                    .collect(Collectors.toMap(
                            r -> r.getName().trim(),
                            TempRecord::getId,
                            (a, b) -> a));

            // 构建并插入 TempAttendanceRecord
            List<TempAttendanceRecord> tempAtts = new ArrayList<>();
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
                    ta.setSourceFile(ar.getSourceFile());
                    if (ar.getName() != null) {
                        ta.setTempRecordId(nameToTempId.get(ar.getName().trim()));
                    }
                    tempAtts.add(ta);
                }
            }
            if (!tempAtts.isEmpty()) {
                tempAttendanceRecordMapper.insertBatch(tempAtts);
            }

            result.put("code", 200);
            result.put("batchId", batchId);
            result.put("data", records);
            result.put("total", records.size());
            result.put("attendances", tempAtts);
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
        List<ConflictResult> conflicts = verifyService.check(batchId);
        result.put("code", 200);
        result.put("pass", conflicts.isEmpty());
        result.put("conflicts", conflicts);
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
        List<ConflictResult> conflicts = verifyService.check(batchId);
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
        result.put("code", 200);
        result.put("data", records);
        result.put("attendances", atts);
        return result;
    }
}
