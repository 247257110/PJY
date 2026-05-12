package org.example.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;

import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.TableIterator;
import org.apache.poi.hwpf.usermodel.TableRow;
import org.apache.poi.hwpf.usermodel.TableCell;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.example.entity.AttendanceRecord;
import org.example.entity.ParseResult;
import org.example.entity.TempRecord;
import org.example.entity.WorkRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfParseService {

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy年MM月dd日"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd")
    };

    public List<TempRecord> parse(MultipartFile file, String batchId) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) throw new IllegalArgumentException("文件名不能为空");
        String lower = filename.toLowerCase();

        if (lower.endsWith(".pdf")) {
            return parsePdf(file.getInputStream(), batchId, filename);
        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseExcel(file.getInputStream(), batchId, filename);
        } else if (lower.endsWith(".docx")) {
            return parseDocx(file.getInputStream(), batchId, filename);
        } else if (lower.endsWith(".doc")) {
            return parseDoc(file.getInputStream(), batchId, filename);
        } else {
            throw new IllegalArgumentException("不支持的文件格式，请上传 PDF、Excel 或 Word 文件");
        }
    }

    // ── PDF ──────────────────────────────────────────────────────────────────

    private List<TempRecord> parsePdf(InputStream is, String batchId, String filename) throws Exception {
        byte[] bytes = is.readAllBytes();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return parseTextLines(stripper.getText(doc), batchId, filename);
        }
    }

    // ── Word .docx ────────────────────────────────────────────────────────────

    private List<TempRecord> parseDocx(InputStream is, String batchId, String filename) throws Exception {
        List<TempRecord> result = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(is)) {
            // 先尝试从表格提取
            for (XWPFTable table : doc.getTables()) {
                List<TempRecord> rows = extractFromXwpfTable(table, batchId, filename);
                if (!rows.isEmpty()) {
                    result.addAll(rows);
                }
            }
            // 表格没有数据则退回文本解析
            if (result.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append("\n");
                result = parseTextLines(sb.toString(), batchId, filename);
            }
        }
        return result;
    }

    private List<TempRecord> extractFromXwpfTable(XWPFTable table, String batchId, String filename) {
        List<TempRecord> result = new ArrayList<>();
        int headerRow = -1;
        List<XWPFTableRow> rows = table.getRows();

        // 找表头行
        for (int i = 0; i < rows.size(); i++) {
            String rowText = getRowText(rows.get(i));
            if (rowText.contains("公司名称") || rowText.contains("实际开始")) {
                headerRow = i;
                break;
            }
        }
        if (headerRow < 0) return result;

        // 解析表头列顺序
        XWPFTableRow hRow = rows.get(headerRow);
        int[] colIdx = resolveColumnIndex(hRow.getTableCells().stream()
                .map(c -> c.getText().trim()).toArray(String[]::new));

        for (int i = headerRow + 1; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            List<XWPFTableCell> cells = row.getTableCells();
            String[] cols = new String[cells.size()];
            for (int j = 0; j < cells.size(); j++) cols[j] = cells.get(j).getText().trim();
            TempRecord r = buildRecordByIndex(cols, colIdx, batchId, filename);
            if (r != null) result.add(r);
        }
        return result;
    }

    private String getRowText(XWPFTableRow row) {
        StringBuilder sb = new StringBuilder();
        row.getTableCells().forEach(c -> sb.append(c.getText()));
        return sb.toString();
    }

    // ── Word .doc ─────────────────────────────────────────────────────────────

    private List<TempRecord> parseDoc(InputStream is, String batchId, String filename) throws Exception {
        List<TempRecord> result = new ArrayList<>();
        try (HWPFDocument doc = new HWPFDocument(is)) {
            Range range = doc.getRange();

            // 从文档标题段落提取项目名称
            String projectName = null;
            for (int i = 0; i < range.numParagraphs(); i++) {
                Paragraph para = range.getParagraph(i);
                String text = para.text().trim();
                if (para.getTableLevel() == 0 && text.contains("工作内容时间统计表")) {
                    int idx = text.indexOf("验收工作内容时间统计表");
                    if (idx == -1) idx = text.indexOf("工作内容时间统计表");
                    if (idx > 0) projectName = text.substring(0, idx).trim();
                    break;
                }
            }

            TableIterator ti = new TableIterator(range);
            while (ti.hasNext()) {
                org.apache.poi.hwpf.usermodel.Table table = ti.next();
                if (table.numRows() < 2) continue;

                TableRow headerRow = table.getRow(0);
                String[] headers = new String[headerRow.numCells()];
                for (int c = 0; c < headerRow.numCells(); c++)
                    headers[c] = headerRow.getCell(c).text().trim();

                if (!containsHeader(headers)) continue;

                int[] colIdx = resolveColumnIndex(headers);

                for (int r = 1; r < table.numRows(); r++) {
                    TableRow row = table.getRow(r);
                    String[] cols = new String[row.numCells()];
                    for (int c = 0; c < row.numCells(); c++)
                        cols[c] = row.getCell(c).text().trim();
                    TempRecord rec = buildRecordByIndex(cols, colIdx, batchId, filename);
                    if (rec != null) {
                        if (projectName != null) rec.setProjectName(projectName);
                        result.add(rec);
                    }
                }
            }
        }
        return result;
    }

    // ── Excel ─────────────────────────────────────────────────────────────────

    private List<TempRecord> parseExcel(InputStream is, String batchId, String filename) throws Exception {
        List<TempRecord> result = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(is)) {
            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sheet = wb.getSheetAt(si);
                int headerRow = -1;
                int[] colIdx = null;

                for (Row row : sheet) {
                    if (row.getLastCellNum() <= 0) continue;
                    String[] headers = new String[row.getLastCellNum()];
                    for (int ci = 0; ci < row.getLastCellNum(); ci++)
                        headers[ci] = getCellString(row.getCell(ci));
                    if (containsHeader(headers)) {
                        headerRow = row.getRowNum();
                        colIdx = resolveColumnIndex(headers);
                        break;
                    }
                }
                if (headerRow < 0) continue;

                for (int ri = headerRow + 1; ri <= sheet.getLastRowNum(); ri++) {
                    Row row = sheet.getRow(ri);
                    if (row == null || row.getLastCellNum() <= 0) continue;
                    String[] cols = new String[row.getLastCellNum()];
                    for (int ci = 0; ci < row.getLastCellNum(); ci++)
                        cols[ci] = getCellString(row.getCell(ci));
                    TempRecord r = buildRecordByIndex(cols, colIdx, batchId, filename);
                    if (r != null) result.add(r);
                }
            }
        }
        return result;
    }

    // ── 文本行解析（PDF / .doc 退回方案）────────────────────────────────────────

    private List<TempRecord> parseTextLines(String text, String batchId, String filename) {
        List<TempRecord> result = new ArrayList<>();
        String[] lines = text.split("\n");
        boolean inTable = false;
        boolean headerPassed = false;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.contains("工作内容时间统计表")) {
                inTable = true;
                headerPassed = false;
                continue;
            }
            if (!inTable) continue;

            if (!headerPassed && (line.contains("公司名称") || line.contains("姓名") || line.contains("实际开始"))) {
                headerPassed = true;
                continue;
            }
            if (!headerPassed) continue;

            String[] cols = line.split("\t|\\s{2,}");
            if (cols.length >= 6) {
                TempRecord r = buildRecord(cols, batchId, filename);
                if (r != null) result.add(r);
            }
        }
        return result;
    }

    // ── 列索引解析（兼容列顺序不固定的情况）──────────────────────────────────────

    /** 返回 int[8]：0=公司 1=姓名 2=项目 3=开始 4=结束 5=实际人天 6=标准人天 7=工作内容，-1表示未找到 */
    private int[] resolveColumnIndex(String[] headers) {
        int[] idx = {-1, -1, -1, -1, -1, -1, -1, -1};
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i] == null ? "" : headers[i];
            if (h.contains("公司")) idx[0] = i;
            else if (h.contains("姓名") || (h.contains("人员") && !h.contains("资质"))) idx[1] = i;
            else if (h.contains("项目")) idx[2] = i;
            else if (h.contains("开始")) idx[3] = i;
            else if (h.contains("结束")) idx[4] = i;
            else if (h.contains("实际") && h.contains("人天")) idx[5] = i;
            else if (h.contains("标准") || h.contains("实施")) idx[6] = i;
            else if (h.contains("工作内容") || h.contains("备注")) idx[7] = i;
        }
        return idx;
    }

    private boolean containsHeader(String[] headers) {
        for (String h : headers) {
            if (h != null && (h.contains("公司名称") || h.contains("实际开始"))) return true;
        }
        return false;
    }

    private TempRecord buildRecordByIndex(String[] cols, int[] idx, String batchId, String filename) {
        try {
            TempRecord r = new TempRecord();
            r.setBatchId(batchId);
            r.setSourceFile(filename);
            r.setCompanyName(getCol(cols, idx[0]));
            r.setName(getCol(cols, idx[1]));
            r.setProjectName(getCol(cols, idx[2]));
            r.setActualStartDate(parseDate(getCol(cols, idx[3])));
            r.setActualEndDate(parseDate(getCol(cols, idx[4])));
            r.setActualDays(parseBigDecimal(getCol(cols, idx[5])));
            r.setStandardDays(parseBigDecimal(getCol(cols, idx[6])));
            r.setWorkContent(getCol(cols, idx[7]));
            if (r.getName() == null || r.getName().isEmpty()) return null;
            if (r.getActualStartDate() == null || r.getActualEndDate() == null) return null;
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private String getCol(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length) return null;
        String v = cols[idx];
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    // ── 原有固定列顺序解析（文本行退回方案）──────────────────────────────────────

    private TempRecord buildRecord(String[] cols, String batchId, String filename) {
        try {
            TempRecord r = new TempRecord();
            r.setBatchId(batchId);
            r.setSourceFile(filename);
            if (cols.length > 0) r.setCompanyName(cols[0].trim());
            if (cols.length > 1) r.setName(cols[1].trim());
            if (cols.length > 2) r.setProjectName(cols[2].trim());
            if (cols.length > 3) r.setActualStartDate(parseDate(cols[3].trim()));
            if (cols.length > 4) r.setActualEndDate(parseDate(cols[4].trim()));
            if (cols.length > 5) r.setActualDays(parseBigDecimal(cols[5].trim()));
            if (cols.length > 6) r.setStandardDays(parseBigDecimal(cols[6].trim()));
            if (cols.length > 7) r.setWorkContent(cols[7].trim());
            if (r.getName() == null || r.getName().isEmpty()) return null;
            if (r.getActualStartDate() == null || r.getActualEndDate() == null) return null;
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────────

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield String.valueOf((long) cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try { return LocalDate.parse(s, fmt); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private BigDecimal parseBigDecimal(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return new BigDecimal(s.replaceAll("[^\\d.]", "")); } catch (Exception e) { return null; }
    }

    /** Excel / Word 文件直接解析为 WorkRecord（用于批量初始化入库） */
    public List<WorkRecord> parseToWorkRecord(MultipartFile file, String filename) throws Exception {
        String lower = filename == null ? "" : filename.toLowerCase();
        List<TempRecord> temps;
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            temps = parseExcel(file.getInputStream(), "batch", filename);
        } else if (lower.endsWith(".docx")) {
            temps = parseDocx(file.getInputStream(), "batch", filename);
        } else if (lower.endsWith(".doc")) {
            temps = parseDoc(file.getInputStream(), "batch", filename);
        } else {
            throw new IllegalArgumentException("不支持的文件格式");
        }
        List<WorkRecord> result = new ArrayList<>();
        for (TempRecord t : temps) {
            WorkRecord r = new WorkRecord();
            r.setCompanyName(t.getCompanyName());
            r.setName(t.getName());
            r.setProjectName(t.getProjectName());
            r.setActualStartDate(t.getActualStartDate());
            r.setActualEndDate(t.getActualEndDate());
            r.setActualDays(t.getActualDays());
            r.setStandardDays(t.getStandardDays());
            r.setWorkContent(t.getWorkContent());
            r.setSourceFile(filename);
            result.add(r);
        }
        return result;
    }

    /** 解析文件，同时返回工作记录和考勤记录 */
    public ParseResult parseFileToResult(MultipartFile file, String filename) throws Exception {
        String lower = filename == null ? "" : filename.toLowerCase();
        ParseResult result = new ParseResult();
        result.setWorkRecords(new ArrayList<>());
        result.setAttendances(new ArrayList<>());

        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            try (InputStream is = file.getInputStream()) {
                parseExcelToResult(is, filename, result);
            }
        } else if (lower.endsWith(".docx")) {
            try (InputStream is = file.getInputStream()) {
                parseDocxToResult(is, filename, result);
            }
        } else if (lower.endsWith(".doc")) {
            List<TempRecord> temps = parseDoc(file.getInputStream(), "batch", filename);
            result.setWorkRecords(tempsToWorkRecords(temps, filename));
        } else {
            throw new IllegalArgumentException("不支持的文件格式");
        }
        return result;
    }

    private void parseExcelToResult(InputStream is, String filename, ParseResult result) throws Exception {
        try (Workbook wb = WorkbookFactory.create(is)) {
            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sheet = wb.getSheetAt(si);
                String sheetName = sheet.getSheetName();
                if (isAttendanceSheet(sheetName) || isAttendanceContent(sheet)) {
                    result.getAttendances().addAll(parseAttendanceSheet(sheet, filename));
                } else {
                    result.getWorkRecords().addAll(tempsToWorkRecords(
                            parseExcelSheet(sheet, "batch", filename), filename));
                }
            }
        }
    }

    private void parseDocxToResult(InputStream is, String filename, ParseResult result) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(is)) {
            for (XWPFTable table : doc.getTables()) {
                String headerText = table.getRows().isEmpty() ? "" :
                        table.getRow(0).getTableCells().stream()
                                .map(c -> c.getText().trim()).reduce("", String::concat);
                if (isAttendanceHeader(headerText)) {
                    result.getAttendances().addAll(parseAttendanceXwpfTable(table, filename));
                } else {
                    result.getWorkRecords().addAll(tempsToWorkRecords(
                            extractFromXwpfTable(table, "batch", filename), filename));
                }
            }
        }
    }

    private boolean isAttendanceSheet(String name) {
        return name != null && (name.contains("考勤") || name.contains("签到") || name.contains("登记"));
    }

    /** 检查 sheet 内容是否包含考勤关键词（用于名称不匹配时的回退判断） */
    private boolean isAttendanceContent(Sheet sheet) {
        for (int ri = 0; ri <= Math.min(1, sheet.getLastRowNum()); ri++) {
            Row row = sheet.getRow(ri);
            if (row == null || row.getLastCellNum() <= 0) continue;
            for (int ci = 0; ci < row.getLastCellNum(); ci++) {
                String v = getCellString(row.getCell(ci));
                if (v != null && (v.contains("考勤") || v.contains("签到") || v.contains("登记表"))) return true;
            }
        }
        return false;
    }

    /** 从 sheet 名称或内容中提取年月信息 */
    private java.time.YearMonth extractYearMonth(Sheet sheet) {
        // 尝试从 sheet 名称提取：如 "2024年11月 (2)"
        String name = sheet.getSheetName();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月").matcher(name);
        if (m.find()) return java.time.YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        // 尝试从前两行内容提取
        for (int ri = 0; ri <= Math.min(1, sheet.getLastRowNum()); ri++) {
            Row row = sheet.getRow(ri);
            if (row == null || row.getLastCellNum() <= 0) continue;
            for (int ci = 0; ci < row.getLastCellNum(); ci++) {
                String v = getCellString(row.getCell(ci));
                if (v != null) {
                    m = java.util.regex.Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月").matcher(v);
                    if (m.find()) return java.time.YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                }
            }
        }
        return null;
    }

    private boolean isAttendanceHeader(String text) {
        return text != null && (text.contains("考勤") || text.contains("签到") || text.contains("登记表"));
    }

    /** 解析 Excel 考勤登记表 sheet */
    private List<AttendanceRecord> parseAttendanceSheet(Sheet sheet, String filename) {
        List<AttendanceRecord> result = new ArrayList<>();
        int headerRow = -1;
        List<LocalDate> dateCols = new ArrayList<>();
        int nameColIdx = -1;
        int timeColIdx = -1; // 上午/下午 列
        String projectName = null;
        java.time.YearMonth yearMonth = extractYearMonth(sheet);

        for (Row row : sheet) {
            if (row.getLastCellNum() <= 0) continue;
            String[] cells = new String[row.getLastCellNum()];
            for (int ci = 0; ci < row.getLastCellNum(); ci++)
                cells[ci] = getCellString(row.getCell(ci));
            // 尝试找项目名称
            for (String c : cells) {
                if (c != null && c.contains("项目") && projectName == null) {
                    projectName = c.replaceAll("项目[名称：:]*", "").trim();
                }
            }
            // 找含日期的表头行
            int dateCount = 0;
            List<LocalDate> tmpDates = new ArrayList<>();
            int tmpNameIdx = -1;
            int tmpTimeIdx = -1;
            for (int ci = 0; ci < cells.length; ci++) {
                String c = cells[ci];
                if (c == null) continue;
                String stripped = c.replaceAll("\\s+", "");
                if (stripped.contains("姓名") || stripped.contains("人员")) tmpNameIdx = ci;
                if (stripped.contains("时间")) tmpTimeIdx = ci;
                LocalDate d = parseAttendanceDate(c);
                if (d != null) { tmpDates.add(d); dateCount++; }
                else if (yearMonth != null) {
                    // 尝试解析数字作为 day-of-month
                    try {
                        int day = Integer.parseInt(c);
                        if (day >= 1 && day <= 31) {
                            tmpDates.add(yearMonth.atDay(Math.min(day, yearMonth.lengthOfMonth())));
                            dateCount++;
                            continue;
                        }
                    } catch (NumberFormatException ignored) {}
                    tmpDates.add(null);
                } else {
                    tmpDates.add(null);
                }
            }
            if (dateCount >= 3) {
                headerRow = row.getRowNum();
                dateCols = tmpDates;
                nameColIdx = tmpNameIdx;
                timeColIdx = tmpTimeIdx;
                break;
            }
        }
        if (headerRow < 0 || nameColIdx < 0) return result;

        // 用 Map 按 "name|date" 合并上下午记录
        java.util.LinkedHashMap<String, AttendanceRecord> recordMap = new java.util.LinkedHashMap<>();
        String lastName = null;
        boolean isMorning = true; // 默认上午
        for (int ri = headerRow + 1; ri <= sheet.getLastRowNum(); ri++) {
            Row row = sheet.getRow(ri);
            if (row == null || row.getLastCellNum() <= 0) continue;
            String name = getCellString(row.getCell(nameColIdx));
            if (name != null && !name.isBlank()) lastName = name.trim();
            if (lastName == null) continue;
            // 检测上午/下午标记
            if (timeColIdx >= 0) {
                String timeMark = getCellString(row.getCell(timeColIdx));
                if (timeMark != null) {
                    if (timeMark.contains("下") || timeMark.contains("下午")) isMorning = false;
                    else if (timeMark.contains("上") || timeMark.contains("上午")) isMorning = true;
                }
            }
            for (int ci = 0; ci < dateCols.size() && ci < row.getLastCellNum(); ci++) {
                LocalDate date = dateCols.get(ci);
                if (date == null) continue;
                String key = lastName + "|" + date;
                AttendanceRecord ar = recordMap.get(key);
                if (ar == null) {
                    ar = new AttendanceRecord();
                    ar.setName(lastName);
                    ar.setCheckDate(date);
                    ar.setProjectName(projectName);
                    ar.setSourceFile(filename);
                    recordMap.put(key, ar);
                }
                String val = getCellString(row.getCell(ci));
                if (val != null && !val.isBlank()) {
                    if (isMorning) ar.setMorning("有"); else ar.setAfternoon("有");
                }
            }
        }
        // 未标记的统一设为"无"
        for (AttendanceRecord ar : recordMap.values()) {
            if (ar.getMorning() == null) ar.setMorning("无");
            if (ar.getAfternoon() == null) ar.setAfternoon("无");
            result.add(ar);
        }
        return result;
    }

    /** 解析 Word 考勤登记表 */
    private List<AttendanceRecord> parseAttendanceXwpfTable(XWPFTable table, String filename) {
        List<AttendanceRecord> result = new ArrayList<>();
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return result;

        // 找表头行
        int headerRow = -1;
        List<LocalDate> dateCols = new ArrayList<>();
        int nameColIdx = -1;
        String projectName = null;

        for (int i = 0; i < rows.size(); i++) {
            List<XWPFTableCell> cells = rows.get(i).getTableCells();
            String[] texts = cells.stream().map(c -> c.getText().trim()).toArray(String[]::new);
            int dateCount = 0;
            List<LocalDate> tmpDates = new ArrayList<>();
            int tmpNameIdx = -1;
            for (int ci = 0; ci < texts.length; ci++) {
                if (texts[ci].contains("姓名") || texts[ci].contains("人员")) tmpNameIdx = ci;
                if (texts[ci].contains("项目") && projectName == null)
                    projectName = texts[ci].replaceAll("项目[名称：:]*", "").trim();
                LocalDate d = parseAttendanceDate(texts[ci]);
                tmpDates.add(d);
                if (d != null) dateCount++;
            }
            if (dateCount >= 3) {
                headerRow = i;
                dateCols = tmpDates;
                nameColIdx = tmpNameIdx;
                break;
            }
        }
        if (headerRow < 0 || nameColIdx < 0) return result;

        for (int i = headerRow + 1; i < rows.size(); i++) {
            List<XWPFTableCell> cells = rows.get(i).getTableCells();
            if (nameColIdx >= cells.size()) continue;
            String name = cells.get(nameColIdx).getText().trim();
            if (name.isBlank()) continue;
            for (int ci = 0; ci < dateCols.size() && ci < cells.size(); ci++) {
                LocalDate date = dateCols.get(ci);
                if (date == null) continue;
                String val = cells.get(ci).getText().trim();
                if (!val.isBlank()) {
                    AttendanceRecord ar = new AttendanceRecord();
                    ar.setName(name);
                    ar.setCheckDate(date);
                    ar.setProjectName(projectName);
                    ar.setSourceFile(filename);
                    result.add(ar);
                }
            }
        }
        return result;
    }

    /** 解析考勤表头中的日期，支持 "M月D日"、"MM/DD"、"yyyy-MM-dd" 等格式 */
    private LocalDate parseAttendanceDate(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        // yyyy-MM-dd
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd")); } catch (Exception ignored) {}
        // yyyy/MM/dd
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy/MM/dd")); } catch (Exception ignored) {}
        // M月D日 — 年份从当前年推断
        try {
            MonthDay md = MonthDay.parse(s, DateTimeFormatter.ofPattern("M月d日"));
            return md.atYear(LocalDate.now().getYear());
        } catch (Exception ignored) {}
        // MM/DD
        try {
            MonthDay md = MonthDay.parse(s, DateTimeFormatter.ofPattern("MM/dd"));
            return md.atYear(LocalDate.now().getYear());
        } catch (Exception ignored) {}
        return null;
    }

    private List<WorkRecord> tempsToWorkRecords(List<TempRecord> temps, String filename) {
        List<WorkRecord> result = new ArrayList<>();
        for (TempRecord t : temps) {
            WorkRecord r = new WorkRecord();
            r.setCompanyName(t.getCompanyName());
            r.setName(t.getName());
            r.setProjectName(t.getProjectName());
            r.setActualStartDate(t.getActualStartDate());
            r.setActualEndDate(t.getActualEndDate());
            r.setActualDays(t.getActualDays());
            r.setStandardDays(t.getStandardDays());
            r.setWorkContent(t.getWorkContent());
            r.setSourceFile(filename);
            result.add(r);
        }
        return result;
    }

    private List<TempRecord> parseExcelSheet(Sheet sheet, String batchId, String filename) {
        List<TempRecord> result = new ArrayList<>();
        int headerRow = -1;
        int[] colIdx = null;
        for (Row row : sheet) {
            if (row.getLastCellNum() <= 0) continue;
            String[] headers = new String[row.getLastCellNum()];
            for (int ci = 0; ci < row.getLastCellNum(); ci++)
                headers[ci] = getCellString(row.getCell(ci));
            if (containsHeader(headers)) {
                headerRow = row.getRowNum();
                colIdx = resolveColumnIndex(headers);
                break;
            }
        }
        if (headerRow < 0) return result;
        for (int ri = headerRow + 1; ri <= sheet.getLastRowNum(); ri++) {
            Row row = sheet.getRow(ri);
            if (row == null || row.getLastCellNum() <= 0) continue;
            String[] cols = new String[row.getLastCellNum()];
            for (int ci = 0; ci < row.getLastCellNum(); ci++)
                cols[ci] = getCellString(row.getCell(ci));
            TempRecord r = buildRecordByIndex(cols, colIdx, batchId, filename);
            if (r != null) result.add(r);
        }
        return result;
    }
}
