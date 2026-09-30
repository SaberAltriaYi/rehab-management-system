package cn.iocoder.yudao.module.rehab.service.motion;

import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenCap 导出结构白名单与内容嗅探（纯函数，便于单测）。
 * <p>
 * 只接受 OpenCap 导出中分析必需的文件：.mot/.trc/.osim/sessionMetadata.yaml，以及（患者同意时）同步视频。
 * pickle 等可执行序列化格式、未知路径一律拒绝。路径只用于分类，存储文件名由服务端随机生成。
 */
public final class MotionFileRules {

    public static final long MAX_KINEMATIC_BYTES = 16L * 1024 * 1024;
    public static final long MAX_OSIM_BYTES = 8L * 1024 * 1024;
    public static final long MAX_METADATA_BYTES = 256L * 1024;
    public static final long MAX_VIDEO_BYTES = 16L * 1024 * 1024;
    public static final int MAX_PATH_LENGTH = 255;

    /** 与引擎 TRIAL_NAME_RE 保持一致 */
    public static final Pattern TRIAL_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.\\-]{0,127}$");
    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.\\- ]{0,127}$");
    private static final Pattern WRAPPER = Pattern.compile("^OpenCapData_[A-Za-z0-9\\-]{1,64}$");
    private static final Pattern MOT = Pattern.compile("^OpenSimData/Kinematics/([^/]+)\\.mot$");
    private static final Pattern TRC = Pattern.compile("^MarkerData/([^/]+)\\.trc$");
    private static final Pattern OSIM = Pattern.compile("^OpenSimData/Model/([^/]+)\\.osim$");
    private static final Pattern VIDEO = Pattern.compile("^Videos/(Cam[0-9]{1,2})/InputMedia/([^/]+)/([^/]+)\\.(mp4|mov)$",
            Pattern.CASE_INSENSITIVE);

    private MotionFileRules() {
    }

    @Data
    @AllArgsConstructor
    public static class Classified {
        private String kind;
        /** 去掉 OpenCapData_xxx 外层目录后的规范路径 */
        private String relativePath;
        private String trialName;
        private String cameraKey;
        private String extension;
        private long maxBytes;
        private String contentType;
    }

    /**
     * @return 分类结果；路径不安全或不在白名单中时返回 null
     */
    public static Classified classify(String rawPath) {
        String path = normalize(rawPath);
        if (path == null) {
            return null;
        }
        if ("sessionMetadata.yaml".equals(path)) {
            return new Classified(RehabMotionConstants.FILE_METADATA, path, null, null, "yaml", MAX_METADATA_BYTES,
                    "text/yaml");
        }
        Matcher m = MOT.matcher(path);
        if (m.matches()) {
            return trial(RehabMotionConstants.FILE_MOT, path, m.group(1), "mot", MAX_KINEMATIC_BYTES, "text/plain");
        }
        m = TRC.matcher(path);
        if (m.matches()) {
            return trial(RehabMotionConstants.FILE_TRC, path, m.group(1), "trc", MAX_KINEMATIC_BYTES, "text/plain");
        }
        m = OSIM.matcher(path);
        if (m.matches()) {
            if (!TRIAL_NAME.matcher(m.group(1)).matches()) {
                return null;
            }
            return new Classified(RehabMotionConstants.FILE_OSIM, path, null, null, "osim", MAX_OSIM_BYTES,
                    "application/xml");
        }
        m = VIDEO.matcher(path);
        if (m.matches()) {
            if (!TRIAL_NAME.matcher(m.group(2)).matches() || !TRIAL_NAME.matcher(m.group(3)).matches()) {
                return null;
            }
            String ext = m.group(4).toLowerCase(Locale.ROOT);
            return new Classified(RehabMotionConstants.FILE_VIDEO, path, m.group(2), m.group(1), ext, MAX_VIDEO_BYTES,
                    "mp4".equals(ext) ? "video/mp4" : "video/quicktime");
        }
        return null;
    }

    private static Classified trial(String kind, String path, String trial, String ext, long max, String type) {
        if (!TRIAL_NAME.matcher(trial).matches()) {
            return null;
        }
        return new Classified(kind, path, trial, null, ext, max, type);
    }

    /**
     * 规范化浏览器提供的相对路径（webkitRelativePath）。拒绝：绝对路径、盘符、反斜杠、控制字符、
     * 空段、"." / ".." 段、超长路径、不安全字符；允许并去掉一层 OpenCapData_xxx 外层目录。
     */
    public static String normalize(String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || rawPath.length() > MAX_PATH_LENGTH) {
            return null;
        }
        for (int i = 0; i < rawPath.length(); i++) {
            char c = rawPath.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '\\' || c == ':') {
                return null;
            }
        }
        if (rawPath.startsWith("/")) {
            return null;
        }
        String[] parts = rawPath.split("/", -1);
        int start = 0;
        if (parts.length > 1 && WRAPPER.matcher(parts[0]).matches()) {
            start = 1;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || ".".equals(part) || "..".equals(part) || !SAFE_SEGMENT.matcher(part).matches()) {
                return null;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(part);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 按类型检查文件头，防止改扩展名上传任意内容。
     */
    public static boolean contentLooksValid(String kind, byte[] head) {
        if (head == null || head.length == 0) {
            return false;
        }
        if (RehabMotionConstants.FILE_VIDEO.equals(kind)) {
            if (head.length < 12) {
                return false;
            }
            String box = new String(head, 4, 4, StandardCharsets.ISO_8859_1);
            return "ftyp".equals(box) || "moov".equals(box) || "mdat".equals(box) || "wide".equals(box)
                    || "free".equals(box);
        }
        // 其余均为文本格式：不得含 NUL
        for (byte b : head) {
            if (b == 0) {
                return false;
            }
        }
        String text = new String(head, StandardCharsets.UTF_8);
        String lower = text.toLowerCase(Locale.ROOT);
        if (RehabMotionConstants.FILE_MOT.equals(kind)) {
            return lower.contains("endheader");
        }
        if (RehabMotionConstants.FILE_TRC.equals(kind)) {
            return text.startsWith("PathFileType") || text.startsWith("\uFEFFPathFileType");
        }
        if (RehabMotionConstants.FILE_OSIM.equals(kind)) {
            // 拒绝 DOCTYPE/实体声明（XXE / 实体膨胀）
            return lower.contains("<opensimdocument") && !lower.contains("<!doctype") && !lower.contains("<!entity");
        }
        if (RehabMotionConstants.FILE_METADATA.equals(kind)) {
            return !lower.contains("!!python");
        }
        return false;
    }

    /**
     * 上传策略（供前端“自动过滤 + 逐个上传”使用，与服务端校验同源）。
     * 单文件上限不超过 Spring multipart（16MB）与 nginx（32m）限制；服务端仍逐个复核。
     */
    public static Map<String, Object> policy() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> rules = new ArrayList<Map<String, Object>>();
        rules.add(rule(RehabMotionConstants.FILE_METADATA, "^(OpenCapData_[A-Za-z0-9-]{1,64}/)?sessionMetadata\\.yaml$",
                MAX_METADATA_BYTES, true));
        rules.add(rule(RehabMotionConstants.FILE_MOT,
                "^(OpenCapData_[A-Za-z0-9-]{1,64}/)?OpenSimData/Kinematics/[A-Za-z0-9][A-Za-z0-9_.-]{0,127}\\.mot$",
                MAX_KINEMATIC_BYTES, true));
        rules.add(rule(RehabMotionConstants.FILE_TRC,
                "^(OpenCapData_[A-Za-z0-9-]{1,64}/)?MarkerData/[A-Za-z0-9][A-Za-z0-9_.-]{0,127}\\.trc$",
                MAX_KINEMATIC_BYTES, false));
        rules.add(rule(RehabMotionConstants.FILE_OSIM,
                "^(OpenCapData_[A-Za-z0-9-]{1,64}/)?OpenSimData/Model/[A-Za-z0-9][A-Za-z0-9_.-]{0,127}\\.osim$",
                MAX_OSIM_BYTES, false));
        rules.add(rule(RehabMotionConstants.FILE_VIDEO,
                "^(OpenCapData_[A-Za-z0-9-]{1,64}/)?Videos/Cam[0-9]{1,2}/InputMedia/[A-Za-z0-9][A-Za-z0-9_.-]{0,127}/"
                        + "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}\\.(mp4|mov|MP4|MOV)$",
                MAX_VIDEO_BYTES, false));
        out.put("rules", rules);
        out.put("maxPathLength", MAX_PATH_LENGTH);
        out.put("videoRequiresConsent", true);
        out.put("concurrency", 1);
        out.put("maxRetries", 3);
        out.put("skipNote", "pickle/pkl、vtp 几何、OutputMedia 叠加视频、图片与系统文件不参与分析，自动跳过");
        return out;
    }

    private static Map<String, Object> rule(String kind, String pattern, long maxBytes, boolean required) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("kind", kind);
        m.put("pattern", pattern);
        m.put("maxBytes", maxBytes);
        m.put("requiredForAnalysis", required);
        return m;
    }

    /** 文件头嗅探读取的字节数 */
    public static int sniffLength(String kind) {
        return RehabMotionConstants.FILE_VIDEO.equals(kind) ? 16 : 64 * 1024;
    }

}
