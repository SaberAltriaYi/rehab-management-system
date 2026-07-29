package cn.iocoder.yudao.module.infra.framework.file.core.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件类型 Utils
 *
 * @author 芋道源码
 */
public class FileTypeUtils {

    private static final String OCTET_STREAM = "application/octet-stream";

    private static final Map<String, String> MIME_EXTENSION_MAP;

    static {
        Map<String, String> extensions = new HashMap<>();
        extensions.put("image/jpeg", ".jpg");
        extensions.put("image/png", ".png");
        extensions.put("image/gif", ".gif");
        extensions.put("image/webp", ".webp");
        extensions.put("image/bmp", ".bmp");
        extensions.put("image/svg+xml", ".svg");
        extensions.put("application/pdf", ".pdf");
        extensions.put("application/zip", ".zip");
        extensions.put("application/msword", ".doc");
        extensions.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");
        extensions.put("application/vnd.ms-excel", ".xls");
        extensions.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx");
        extensions.put("application/vnd.ms-powerpoint", ".ppt");
        extensions.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx");
        extensions.put("text/plain", ".txt");
        extensions.put("text/csv", ".csv");
        extensions.put("application/json", ".json");
        extensions.put("application/xml", ".xml");
        extensions.put("text/xml", ".xml");
        extensions.put("video/mp4", ".mp4");
        extensions.put("audio/mpeg", ".mp3");
        MIME_EXTENSION_MAP = Collections.unmodifiableMap(extensions);
    }

    /**
     * 获得文件的 mineType，对于 doc，jar 等文件会有误差
     *
     * @param data 文件内容
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    public static String getMineType(byte[] data) {
        return getMineType(data, null);
    }

    /**
     * 已知文件名，获取文件类型，在某些情况下比通过字节数组准确，例如使用 jar 文件时，通过名字更为准确
     *
     * @param name 文件名
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    public static String getMineType(String name) {
        return HttpUtil.getMimeType(name, OCTET_STREAM);
    }

    /**
     * 在拥有文件和数据的情况下，最好使用此方法，最为准确
     *
     * @param data 文件内容
     * @param name 文件名
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    public static String getMineType(byte[] data, String name) {
        String safeName = StrUtil.nullToEmpty(name);
        String extension = FileTypeUtil.getType(new ByteArrayInputStream(data), safeName);
        if (StrUtil.isNotEmpty(extension)) {
            String detectedType = HttpUtil.getMimeType("file." + extension);
            if (StrUtil.isNotEmpty(detectedType)) {
                return detectedType;
            }
        }
        return getMineType(safeName);
    }

    /**
     * 根据 mineType 获得文件后缀
     *
     * 注意：如果获取不到，或者发生异常，都返回 null
     *
     * @param mineType 类型
     * @return 后缀，例如说 .pdf
     */
    public static String getExtension(String mineType) {
        return MIME_EXTENSION_MAP.get(mineType);
    }

    /**
     * 返回附件
     *
     * @param response 响应
     * @param filename 文件名
     * @param content  附件内容
     */
    public static void writeAttachment(HttpServletResponse response, String filename, byte[] content) throws IOException {
        // 设置 header 和 contentType
        String mineType = getMineType(content, filename);
        response.setContentType(mineType);
        // 设置内容显示、下载文件名：https://www.cnblogs.com/wq-9/articles/12165056.html
        if (isImage(mineType)) {
            // 参见 https://github.com/YunaiV/ruoyi-vue-pro/issues/692 讨论
            response.setHeader("Content-Disposition", "inline;filename=" + HttpUtils.encodeUtf8(filename));
        } else {
            response.setHeader("Content-Disposition", "attachment;filename=" + HttpUtils.encodeUtf8(filename));
        }
        // 针对 video 的特殊处理，解决视频地址在移动端播放的兼容性问题
        if (StrUtil.containsIgnoreCase(mineType, "video")) {
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Length", String.valueOf(content.length));
        }
        // 输出附件
        IoUtil.write(response.getOutputStream(), false, content);
    }

    /**
     * 判断是否是图片
     *
     * @param mineType 类型
     * @return 是否是图片
     */
    public static boolean isImage(String mineType) {
        return StrUtil.startWith(mineType, "image/");
    }

}
