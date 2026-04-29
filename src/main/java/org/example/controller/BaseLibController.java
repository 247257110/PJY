package org.example.controller;

import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.example.config.DataPermissionHelper;
import org.example.entity.AttendanceRecord;
import org.example.entity.ParseResult;
import org.example.entity.SysOrg;
import org.example.entity.SysUser;
import org.example.entity.WorkRecord;
import org.example.service.AiParseService;
import org.example.service.BaseLibService;
import org.example.service.PdfParseService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/base-lib")
@RequiredArgsConstructor
public class BaseLibController {

    private final BaseLibService baseLibService;
    private final PdfParseService pdfParseService;
    private final AiParseService aiParseService;
    private final DataPermissionHelper dataPermissionHelper;

    @GetMapping("/list")
    public Map<String, Object> list(
            Authentication auth,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String projectName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!dataPermissionHelper.isAdmin(auth)) {
            SysOrg org = dataPermissionHelper.currentOrg(auth);
            if (org != null) companyName = org.getOrgName();
        }
        PageInfo<WorkRecord> pageInfo = baseLibService.list(name, companyName, projectName, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", pageInfo);
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        baseLibService.deleteById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "删除成功");
        return result;
    }

    @GetMapping("/attendance")
    public Map<String, Object> attendance(@RequestParam Long workRecordId) {
        Map<String, Object> detail = baseLibService.getAttendanceDetail(workRecordId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", detail);
        return result;
    }

    /** 解析文件，返回预览数据（不入库） */
    @PostMapping("/batch-parse")
    public Map<String, Object> batchParse(
            Authentication auth,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "orgId", required = false) Long orgId) {

        if (!dataPermissionHelper.isAdmin(auth) && orgId == null) {
            SysUser currentUser = dataPermissionHelper.currentUser(auth);
            orgId = currentUser.getOrgId();
        }
        String resolvedOrgName = resolveOrgName(auth, orgId, companyName);

        List<WorkRecord> allWorkRecords = new ArrayList<>();
        List<AttendanceRecord> allAttendances = new ArrayList<>();
        List<Map<String, Object>> fileResults = new ArrayList<>();

        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            Map<String, Object> fileResult = new HashMap<>();
            fileResult.put("filename", filename);
            try {
                ParseResult parsed = parseFile(file, filename);
                List<WorkRecord> records = parsed.getWorkRecords();
                applyOrgInfo(records, companyName, orgId, resolvedOrgName);
                allWorkRecords.addAll(records);
                if (parsed.getAttendances() != null) allAttendances.addAll(parsed.getAttendances());
                fileResult.put("success", true);
                fileResult.put("count", records.size());
            } catch (Exception e) {
                e.printStackTrace();
                fileResult.put("success", false);
                fileResult.put("error", e.getMessage());
            }
            fileResults.add(fileResult);
        }

        // 计算考勤校验（不写库）
        baseLibService.verifyAttendance(allWorkRecords, allAttendances);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("workRecords", allWorkRecords);
        result.put("attendances", allAttendances);
        result.put("files", fileResults);
        return result;
    }

    /** 将预览确认的数据入库 */
    @PostMapping("/batch-save")
    public Map<String, Object> batchSave(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wrMaps = (List<Map<String, Object>>) body.get("workRecords");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attMaps = (List<Map<String, Object>>) body.get("attendances");

        List<WorkRecord> workRecords = convertWorkRecords(wrMaps);
        List<AttendanceRecord> attendances = convertAttendances(attMaps);

        baseLibService.insertBatchWithVerification(workRecords, attendances);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("total", workRecords.size());
        return result;
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    private ParseResult parseFile(MultipartFile file, String filename) throws Exception {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")
                || lower.endsWith(".docx") || lower.endsWith(".doc")) {
            return pdfParseService.parseFileToResult(file, filename);
        }
        return aiParseService.parseFileToResultKimi(file, filename);
    }

    private String resolveOrgName(Authentication auth, Long orgId, String companyName) {
        if (orgId == null) return null;
        SysOrg org = dataPermissionHelper.currentOrg(auth);
        if (org != null && org.getId().equals(orgId)) return org.getOrgName();
        return companyName;
    }

    private void applyOrgInfo(List<WorkRecord> records, String companyName, Long orgId, String orgName) {
        if (companyName != null && !companyName.isBlank())
            records.forEach(r -> r.setCompanyName(companyName));
        if (orgId != null) {
            records.forEach(r -> { r.setOrgId(orgId); r.setOrgName(orgName); });
        }
    }

    private List<WorkRecord> convertWorkRecords(List<Map<String, Object>> maps) {
        List<WorkRecord> list = new ArrayList<>();
        if (maps == null) return list;
        for (Map<String, Object> m : maps) {
            WorkRecord r = new WorkRecord();
            r.setCompanyName(str(m, "companyName"));
            r.setName(str(m, "name"));
            r.setProjectName(str(m, "projectName"));
            r.setActualStartDate(m.get("actualStartDate") != null
                    ? java.time.LocalDate.parse(str(m, "actualStartDate")) : null);
            r.setActualEndDate(m.get("actualEndDate") != null
                    ? java.time.LocalDate.parse(str(m, "actualEndDate")) : null);
            r.setActualDays(m.get("actualDays") != null
                    ? new java.math.BigDecimal(str(m, "actualDays")) : null);
            r.setStandardDays(m.get("standardDays") != null
                    ? new java.math.BigDecimal(str(m, "standardDays")) : null);
            r.setWorkContent(str(m, "workContent"));
            r.setSourceFile(str(m, "sourceFile"));
            r.setOrgId(m.get("orgId") != null ? Long.valueOf(str(m, "orgId")) : null);
            r.setOrgName(str(m, "orgName"));
            r.setAttendanceVerified(m.get("attendanceVerified") != null
                    ? Integer.valueOf(str(m, "attendanceVerified")) : null);
            list.add(r);
        }
        return getWorkRecords(list);
    }

    private static List<WorkRecord> getWorkRecords(List<WorkRecord> list) {
        return list;
    }

    private List<AttendanceRecord> convertAttendances(List<Map<String, Object>> maps) {
        List<AttendanceRecord> list = new ArrayList<>();
        if (maps == null) return list;
        for (Map<String, Object> m : maps) {
            AttendanceRecord ar = new AttendanceRecord();
            ar.setName(str(m, "name"));
            ar.setProjectName(str(m, "projectName"));
            ar.setCheckDate(m.get("checkDate") != null
                    ? java.time.LocalDate.parse(str(m, "checkDate")) : null);
            ar.setMorning(str(m, "morning"));
            ar.setAfternoon(str(m, "afternoon"));
            ar.setSourceFile(str(m, "sourceFile"));
            list.add(ar);
        }
        return list;
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    @PostMapping("/batch-init")
    public Map<String, Object> batchInit(
            Authentication auth,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "orgId", required = false) Long orgId) {

        if (!dataPermissionHelper.isAdmin(auth) && orgId == null) {
            SysUser currentUser = dataPermissionHelper.currentUser(auth);
            orgId = currentUser.getOrgId();
        }

        String resolvedOrgName = null;
        if (orgId != null) {
            SysOrg org = dataPermissionHelper.currentOrg(auth);
            if (org != null && org.getId().equals(orgId)) {
                resolvedOrgName = org.getOrgName();
            } else {
                resolvedOrgName = companyName;
            }
        }

        List<Map<String, Object>> fileResults = new ArrayList<>();
        int totalRecords = 0;

        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            Map<String, Object> fileResult = new HashMap<>();
            fileResult.put("filename", filename);
            try {
                ParseResult parsed;
                String lower = filename == null ? "" : filename.toLowerCase();
                if (lower.endsWith(".xlsx") || lower.endsWith(".xls")
                        || lower.endsWith(".docx") || lower.endsWith(".doc")) {
                    parsed = pdfParseService.parseFileToResult(file, filename);
                } else {
                    parsed = aiParseService.parseFileToResultKimi(file, filename);
                }

                List<WorkRecord> records = parsed.getWorkRecords();
                if (companyName != null && !companyName.isBlank()) {
                    records.forEach(r -> r.setCompanyName(companyName));
                }
                if (orgId != null) {
                    final Long orgIdFinal = orgId;
                    final String orgNameFinal = resolvedOrgName;
                    records.forEach(r -> { r.setOrgId(orgIdFinal); r.setOrgName(orgNameFinal); });
                }

                baseLibService.insertBatchWithVerification(records, parsed.getAttendances());
                totalRecords += records.size();
                fileResult.put("success", true);
                fileResult.put("count", records.size());
            } catch (Exception e) {
                fileResult.put("success", false);
                fileResult.put("error", e.getMessage());
            }
            fileResults.add(fileResult);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("total", totalRecords);
        result.put("files", fileResults);
        return result;
    }
}
