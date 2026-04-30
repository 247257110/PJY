package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.example.entity.AttendanceRecord;
import org.example.entity.ParseResult;
import org.example.entity.TempRecord;
import org.example.entity.WorkRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class AiParseService {

    @Value("${doubao.api-key}")
    private String apiKey;

    @Value("${doubao.endpoint}")
    private String endpoint;

    @Value("${doubao.model}")
    private String model;

    @Value("${kimi.api-key}")
    private String kimiApiKey;

    @Value("${kimi.endpoint}")
    private String kimiEndpoint;

    @Value("${kimi.model}")
    private String kimiModel;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PROMPT = """
            请从这份文档中找到"工作内容时间统计表"，提取其中所有数据行。
            以JSON数组格式返回，每行格式如下：
            {"companyName":"公司名称","name":"姓名","projectName":"项目名称","actualStartDate":"yyyy-MM-dd","actualEndDate":"yyyy-MM-dd","actualDays":0.0,"standardDays":0.0,"workContent":"工作内容"}
            注意：
            1. 日期格式统一转换为 yyyy-MM-dd
            2. 人天数值为数字类型
            3. 只返回JSON数组，不要任何其他文字
            4. 如果没有找到工作内容时间统计表，返回空数组 []
            """;

    private static final String PROMPT_ATTENDANCE = """
            请从这份文档中找到”合作公司人员考勤登记表”，提取考勤数据。
            表格结构：
            - 表格上方标注年份和月份，如”2024年7月”
            - 第一列为姓名，之后每个日期（1日~31日）分上午/下午两个子列
            - 判断规则：单元格内存在任何人为留下的痕迹（包括但不限于：打钩✓/✔/√、手写签字、潦草线条、墨迹、文字、数字、印章）→ “有”；单元格完全空白，或仅含表格线交叉阴影、扫描水印、均匀噪点（整体灰色但无局部深色墨迹）→ “无”
            - 关键区分：若单元格内有任何局部深色、不规则的笔迹痕迹，即使非常潦草也应判断为”有”
            以JSON数组格式返回，每条记录对应一人一天：
            [{“projectName”:”项目名称”,”name”:”姓名”,”checkDate”:”yyyy-MM-dd”,”morning”:”有/无”,”afternoon”:”有/无”}]
            提取规则：
            1. projectName：表格上方”信息技术服务项目名称”栏的内容
            2. name：姓名列真实人员姓名，忽略”合计”等汇总行
            3. checkDate：年月取自表格标注 + 日期列组合，格式 yyyy-MM-dd
            4. 只返回JSON数组，不要任何其他文字
            5. 找不到考勤登记表则返回 []
            """;

    private static final String PROMPT_FULL = """
            请从这份文档中提取两类表格的数据，以JSON对象格式返回：
            {
              “workRecords”: [
                {“companyName”:”公司名称”,”name”:”姓名”,”projectName”:”项目名称”,”actualStartDate”:”yyyy-MM-dd”,”actualEndDate”:”yyyy-MM-dd”,”actualDays”:0.0,”standardDays”:0.0,”workContent”:”工作内容”}
              ],
              “attendances”: [
                {“projectName”:”项目名称”,”name”:”姓名”,”checkDate”:”yyyy-MM-dd”,”morning”:”有/无”,”afternoon”:”有/无”}
              ]
            }
            说明：
            1. workRecords 来自”工作内容时间统计表”，日期格式统一为 yyyy-MM-dd，人天为数字
            2. attendances 来自”合作公司人员考勤登记表”，提取规则如下：
               - projectName：表格上方信息技术服务项目名称的内容
               - name：只提取姓名列里的真实人员姓名，忽略无关文字
               - checkDate：年月从表格上方提取，日期范围1日—31日，组合为 yyyy-MM-dd 格式
               - morning：时间列1-31与人员姓名行的"上午"单元格内存在任何人为留下的手写签字痕迹→ “有”；单元格完全空白，或仅含表格线交叉阴影、扫描水印、均匀噪点（整体灰色但无局部深色墨迹）→ “无”
               - afternoon：时间列1-31与人员姓名行的"下午"单元格内存在任何人为留下的手写签字痕迹→ “有”；单元格完全空白，或仅含表格线交叉阴影、扫描水印、均匀噪点（整体灰色但无局部深色墨迹）→ “无”
               - 判断标准：宽松识别人为痕迹，严格排除纯几何特征（格线、均匀阴影）
               - 按以上要求100%解析文档，每条数据包含：projectName、name、checkDate、morning、afternoon
            3. 只返回JSON对象，不要任何其他文字
            4. 找不到对应表格则对应数组返回 []
           """;

    /** 校验模块使用：解析图片/PDF，返回 TempRecord */
    public List<TempRecord> parseToTempRecord(MultipartFile file, String batchId, String filename) throws Exception {
        List<WorkRecord> records = callApi(file, filename, PROMPT, false);
        List<TempRecord> result = new ArrayList<>();
        for (WorkRecord w : records) {
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
            result.add(t);
        }
        return result;
    }

    /** 批量初始化模块使用：解析图片/PDF，返回 WorkRecord */
    public List<WorkRecord> parseFile(MultipartFile file, String filename) throws Exception {
        return callApi(file, filename, PROMPT, false);
    }

    /** 项目验收材料校验 解析图片/PDF，同时返回工作记录和考勤记录 */
    public ParseResult parseFileToResult(MultipartFile file, String filename) throws Exception {
        return callApiForResult(file, filename);
    }

    // ===== Kimi 解析（与豆包对称，仅 sendRequest 使用 Kimi API） =====

    /** 校验模块：Kimi 解析图片/PDF → TempRecord */
    public List<TempRecord> parseToTempRecordKimi(MultipartFile file, String batchId, String filename) throws Exception {
        List<WorkRecord> records = callApiKimi(file, filename, PROMPT);
        List<TempRecord> result = new ArrayList<>();
        for (WorkRecord w : records) {
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
            result.add(t);
        }
        return result;
    }

    /** 批量初始化：Kimi 仅返回工作记录 */
    public List<WorkRecord> parseFileKimi(MultipartFile file, String filename) throws Exception {
        return callApiKimi(file, filename, PROMPT);
    }

    /** 项目验收材料校验：Kimi 返回工作记录+考勤 */
    public ParseResult parseFileToResultKimi(MultipartFile file, String filename) throws Exception {
        return callApiForResultKimi(file, filename);
    }

    private static final long IMAGE_SIZE_LIMIT = 9 * 1024 * 1024; // 9MB，留余量

    private List<WorkRecord> callApi(MultipartFile file, String filename, String prompt, boolean unused) throws Exception {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return callApiPdf(file.getBytes(), prompt, filename);
        }
        byte[] bytes = prepareImageBytes(file.getBytes(), filename);
        String base64Data = Base64.getEncoder().encodeToString(bytes);
        ArrayNode contentArray = buildImageContentArray(base64Data, lower, prompt);
        return parseJson(sendRequest(contentArray), filename);
    }

    private ParseResult callApiForResult(MultipartFile file, String filename) throws Exception {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return callApiPdfForResult(file.getBytes(), filename);
        }
        byte[] bytes = prepareImageBytes(file.getBytes(), filename);
        String base64Data = Base64.getEncoder().encodeToString(bytes);

        ParseResult result = new ParseResult();
        result.setWorkRecords(new ArrayList<>());
        result.setAttendances(new ArrayList<>());

        ArrayNode content1 = buildImageContentArray(base64Data, lower, PROMPT);
        result.getWorkRecords().addAll(parseJson(sendRequest(content1), filename));

        ArrayNode content2 = buildImageContentArray(base64Data, lower, PROMPT_ATTENDANCE);
        result.getAttendances().addAll(parseAttendanceJson(sendRequest(content2), filename));

        return result;
    }

    /** PDF 转图片后逐页发送，合并 WorkRecord 结果 */
    private List<WorkRecord> callApiPdf(byte[] pdfBytes, String prompt, String filename) throws Exception {
        List<BufferedImage> pages = pdfToImages(pdfBytes);
        List<WorkRecord> all = new ArrayList<>();
        for (BufferedImage page : pages) {
            byte[] imgBytes = safeCompressForApi(page);
            String base64 = Base64.getEncoder().encodeToString(imgBytes);
            ArrayNode content = buildImageContentArray(base64, ".jpg", prompt);
            all.addAll(parseJson(sendRequest(content), filename));
        }
        return all;
    }

    /** PDF 转图片后逐页发送，合并 ParseResult 结果 */
    private ParseResult callApiPdfForResult(byte[] pdfBytes, String filename) throws Exception {
        List<BufferedImage> pages = pdfToImages(pdfBytes);
        ParseResult merged = new ParseResult();
        merged.setWorkRecords(new ArrayList<>());
        merged.setAttendances(new ArrayList<>());
        for (BufferedImage page : pages) {
            byte[] imgBytes = safeCompressForApi(page);
            String base64 = Base64.getEncoder().encodeToString(imgBytes);

            ArrayNode content1 = buildImageContentArray(base64, ".jpg", PROMPT);
            merged.getWorkRecords().addAll(parseJson(sendRequest(content1), filename));

            ArrayNode content2 = buildImageContentArray(base64, ".jpg", PROMPT_ATTENDANCE);
            merged.getAttendances().addAll(parseAttendanceJson(sendRequest(content2), filename));
        }
        return merged;
    }

    private List<BufferedImage> pdfToImages(byte[] pdfBytes) throws Exception {
        List<BufferedImage> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                pages.add(renderer.renderImageWithDPI(i, 200));
            }
        }
        return pages;
    }

    // ===== Kimi 内部调用方法 =====

    private List<WorkRecord> callApiKimi(MultipartFile file, String filename, String prompt) throws Exception {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return callApiPdfKimi(file.getBytes(), prompt, filename);
        }
        byte[] bytes = prepareImageBytes(file.getBytes(), filename);
        String base64Data = Base64.getEncoder().encodeToString(bytes);
        ArrayNode contentArray = buildImageContentArray(base64Data, lower, prompt);
        return parseJson(sendRequestKimi(contentArray), filename);
    }

    private List<WorkRecord> callApiPdfKimi(byte[] pdfBytes, String prompt, String filename) throws Exception {
        List<BufferedImage> pages = pdfToImages(pdfBytes);
        List<WorkRecord> all = new ArrayList<>();
        for (BufferedImage page : pages) {
            byte[] imgBytes = safeCompressForApi(page);
            String base64 = Base64.getEncoder().encodeToString(imgBytes);
            ArrayNode content = buildImageContentArray(base64, ".jpg", prompt);
            all.addAll(parseJson(sendRequestKimi(content), filename));
        }
        return all;
    }

    private ParseResult callApiForResultKimi(MultipartFile file, String filename) throws Exception {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return callApiPdfForResultKimi(file.getBytes(), filename);
        }
        byte[] bytes = prepareImageBytes(file.getBytes(), filename);
        String base64Data = Base64.getEncoder().encodeToString(bytes);

        ParseResult result = new ParseResult();
        result.setWorkRecords(new ArrayList<>());
        result.setAttendances(new ArrayList<>());

       // ArrayNode content1 = buildImageContentArray(base64Data, lower, PROMPT);
       // result.getWorkRecords().addAll(parseJson(sendRequestKimi(content1), filename));

      //  ArrayNode content2 = buildImageContentArray(base64Data, lower, PROMPT_ATTENDANCE);
       // result.getAttendances().addAll(parseAttendanceJson(sendRequestKimi(content2), filename));

        //工作记录和考勤记录
        ArrayNode content_full = buildImageContentArray(base64Data, ".jpg", PROMPT_FULL);
        result = parseFullJson(sendRequestKimi(content_full), filename);

        return result;
    }

    private ParseResult callApiPdfForResultKimi(byte[] pdfBytes, String filename) throws Exception {
        List<BufferedImage> pages = pdfToImages(pdfBytes);
        ParseResult merged = new ParseResult();
        merged.setWorkRecords(new ArrayList<>());
        merged.setAttendances(new ArrayList<>());
        System.out.println("page->>>>>"+pages.size());
        int i = 1;
        for (BufferedImage page : pages) {
            System.out.println("----->>>>第"+i+"页解析中........请稍后.......");
            byte[] imgBytes = safeCompressForApi(page);
            String base64 = Base64.getEncoder().encodeToString(imgBytes);

            // Pass 1: 工作记录
          //  ArrayNode content1 = buildImageContentArray(base64, ".jpg", PROMPT);
          //  merged.getWorkRecords().addAll(parseJson(sendRequestKimi(content1), filename));

            // Pass 2: 考勤记录
          //  ArrayNode content2 = buildImageContentArray(base64, ".jpg", PROMPT_ATTENDANCE);
          //  merged.getAttendances().addAll(parseAttendanceJson(sendRequestKimi(content2), filename));

            //工作记录和考勤记录
            ArrayNode content_full = buildImageContentArray(base64, ".jpg", PROMPT_FULL);
            ParseResult  fullMerged = parseFullJson(sendRequestKimi(content_full), filename);
            merged.getWorkRecords().addAll(fullMerged.getWorkRecords());
            merged.getAttendances().addAll(fullMerged.getAttendances()) ;
            i++;
        }
        return merged;
    }

    /**
     * 对图片文件做压缩，确保不超过 AI API 的 10MB 限制。
     */
    private byte[] prepareImageBytes(byte[] original, String filename) throws Exception {
        if (original.length <= IMAGE_SIZE_LIMIT) {
            return original;
        }
        // 读取图片并逐步降低质量/尺寸直到满足限制
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
        if (img == null) return original; // 无法识别格式，原样返回

        // 先尝试降质量压缩（JPEG）
        float quality = 0.85f;
        while (quality >= 0.3f) {
            byte[] compressed = compressJpeg(img, quality);
            if (compressed.length <= IMAGE_SIZE_LIMIT) return compressed;
            quality -= 0.15f;
        }

        // 质量压缩不够，缩小尺寸
        int w = img.getWidth();
        int h = img.getHeight();
        while (w > 100) {
            w = (int) (w * 0.75);
            h = (int) (h * 0.75);
            BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, w, h, null);
            g.dispose();
            byte[] compressed = compressJpeg(scaled, 0.85f);
            if (compressed.length <= IMAGE_SIZE_LIMIT) return compressed;
        }
        return original;
    }

    private byte[] compressJpeg(BufferedImage img, float quality) throws Exception {
        // 确保是 RGB（JPEG 不支持透明通道）
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        writer.setOutput(ImageIO.createImageOutputStream(out));
        writer.write(null, new IIOImage(rgb, null, null), param);
        writer.dispose();
        return out.toByteArray();
    }

    /**
     * 将 BufferedImage 压缩到 IMAGE_SIZE_LIMIT 以内，
     * 优先保持高质量（0.85），超限时缩小尺寸而非降低质量，保护 OCR 识别精度。
     */
    private byte[] safeCompressForApi(BufferedImage img) throws Exception {
        byte[] result = compressJpeg(img, 0.85f);
        if (result.length <= IMAGE_SIZE_LIMIT) return result;

        // 尺寸不断缩小 75%，保持质量 0.85
        int w = img.getWidth();
        int h = img.getHeight();
        while (w > 200) {
            w = (int) (w * 0.75);
            h = (int) (h * 0.75);
            BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, w, h, null);
            g.dispose();
            result = compressJpeg(scaled, 0.85f);
            if (result.length <= IMAGE_SIZE_LIMIT) return result;
        }
        return result;
    }

    private ArrayNode buildImageContentArray(String base64Data, String lower, String prompt) {
        ArrayNode contentArray = MAPPER.createArrayNode();
        String mediaType = getImageMediaType(lower);
        ObjectNode imageItem = MAPPER.createObjectNode();
        imageItem.put("type", "image_url");
        ObjectNode imageUrl = MAPPER.createObjectNode();
        imageUrl.put("url", "data:" + mediaType + ";base64," + base64Data);
        imageItem.set("image_url", imageUrl);
        contentArray.add(imageItem);
        ObjectNode textItem = MAPPER.createObjectNode();
        textItem.put("type", "text");
        textItem.put("text", prompt);
        contentArray.add(textItem);
        return contentArray;
    }

    private String sendRequest(ArrayNode contentArray) throws Exception {
        return sendRequest(contentArray, endpoint, apiKey, model);
    }

    private String sendRequestKimi(ArrayNode contentArray) throws Exception {
        return sendRequest(contentArray, kimiEndpoint, kimiApiKey, kimiModel);
    }

    private String sendRequest(ArrayNode contentArray, String ep, String key, String mdl) throws Exception {
        ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put("model", mdl);
        requestBody.put("max_tokens", 16384);
        ArrayNode messages = MAPPER.createArrayNode();
        ObjectNode userMsg = MAPPER.createObjectNode();
        userMsg.put("role", "user");
        userMsg.set("content", contentArray);
        messages.add(userMsg);
        requestBody.set("messages", messages);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(key);
        HttpEntity<String> entity = new HttpEntity<>(MAPPER.writeValueAsString(requestBody), headers);
        ResponseEntity<String> response = restTemplate.exchange(
            ep + "/chat/completions", HttpMethod.POST, entity, String.class);
        JsonNode root = MAPPER.readTree(response.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText("[]");
   //     System.out.println("[AI响应长度] " + content.length() + " chars");
        System.out.println("[AIcontent]--->" + content);
        return content;
    }

    private ParseResult parseFullJson(String json, String filename) {
        ParseResult result = new ParseResult();
        result.setWorkRecords(new ArrayList<>());
        result.setAttendances(new ArrayList<>());
        try {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start == -1 || end == -1) return result;
            JsonNode root = MAPPER.readTree(json.substring(start, end + 1));
            JsonNode wrArr = root.path("workRecords");
            System.out.println("workRecords---->"+wrArr.toString());
            if (wrArr.isArray()) {
                for (JsonNode node : wrArr) {
                    WorkRecord r = new WorkRecord();
                    r.setCompanyName(getText(node, "companyName"));
                    r.setName(getText(node, "name"));
                    r.setProjectName(getText(node, "projectName"));
                    r.setActualStartDate(parseDate(getText(node, "actualStartDate")));
                    r.setActualEndDate(parseDate(getText(node, "actualEndDate")));
                    r.setActualDays(getDecimal(node, "actualDays"));
                    r.setStandardDays(getDecimal(node, "standardDays"));
                    r.setWorkContent(getText(node, "workContent"));
                    r.setSourceFile(filename);
                    if (r.getName() != null && !r.getName().isEmpty()
                            && r.getActualStartDate() != null && r.getActualEndDate() != null) {
                        result.getWorkRecords().add(r);
                    }
                }
               // System.out.println("[解析结果] workRecords=" + result.getWorkRecords().toString());
            }

            JsonNode attArr = root.path("attendances");

            System.out.println("attendances---->"+attArr.toString());
            if (attArr.isArray()) {
                for (JsonNode node : attArr) {
                    String name = getText(node, "name");
                    LocalDate checkDate = parseDate(getText(node, "checkDate"));
                    if (name == null || checkDate == null) {
                        System.out.println("[WARN] 跳过考勤记录: name=" + getText(node, "name") + ", checkDate=" + getText(node, "checkDate"));
                        continue;
                    }
                    AttendanceRecord ar = new AttendanceRecord();
                    ar.setName(name);
                    ar.setProjectName(getText(node, "projectName"));
                    ar.setCheckDate(checkDate);
                    ar.setMorning(getText(node, "morning"));
                    ar.setAfternoon(getText(node, "afternoon"));
                    ar.setSourceFile(filename);
                    result.getAttendances().add(ar);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private List<AttendanceRecord> parseAttendanceJson(String json, String filename) {
        List<AttendanceRecord> result = new ArrayList<>();
        try {
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start == -1 || end == -1) return result;
            JsonNode arr = MAPPER.readTree(json.substring(start, end + 1));
            for (JsonNode node : arr) {
                String name = getText(node, "name");
                LocalDate checkDate = parseDate(getText(node, "checkDate"));
                if (name == null || checkDate == null) {
                    System.out.println("[WARN] 跳过考勤: name=" + getText(node, "name")
                            + ", date=" + getText(node, "checkDate"));
                    continue;
                }
                AttendanceRecord ar = new AttendanceRecord();
                ar.setName(name);
                ar.setProjectName(getText(node, "projectName"));
                ar.setCheckDate(checkDate);
                ar.setMorning(getText(node, "morning"));
                ar.setAfternoon(getText(node, "afternoon"));
                ar.setSourceFile(filename);
                result.add(ar);
            }
        } catch (Exception e) {
            System.out.println("[ERROR] parseAttendanceJson失败: " + e.getMessage());
        }
        return result;
    }

    private List<WorkRecord> parseJson(String json, String filename) {
        List<WorkRecord> result = new ArrayList<>();
        try {
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start == -1 || end == -1) return result;
            json = json.substring(start, end + 1);

            JsonNode arr = MAPPER.readTree(json);
            for (JsonNode node : arr) {
                WorkRecord r = new WorkRecord();
                r.setCompanyName(getText(node, "companyName"));
                r.setName(getText(node, "name"));
                r.setProjectName(getText(node, "projectName"));
                r.setActualStartDate(parseDate(getText(node, "actualStartDate")));
                r.setActualEndDate(parseDate(getText(node, "actualEndDate")));
                r.setActualDays(getDecimal(node, "actualDays"));
                r.setStandardDays(getDecimal(node, "standardDays"));
                r.setWorkContent(getText(node, "workContent"));
                r.setSourceFile(filename);
                if (r.getName() != null && !r.getName().isEmpty()
                        && r.getActualStartDate() != null && r.getActualEndDate() != null) {
                    result.add(r);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private String getText(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n == null || n.isNull()) ? null : n.asText().trim();
    }

    private BigDecimal getDecimal(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        try { return new BigDecimal(n.asText().replaceAll("[^\\d.]", "")); } catch (Exception e) { return null; }
    }

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"),
            DateTimeFormatter.ofPattern("yyyy年MM月dd日")
    );

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String trimmed = s.trim();
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try { return LocalDate.parse(trimmed, fmt); } catch (Exception ignored) {}
        }
        return null;
    }

    private String getImageMediaType(String lower) {
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
