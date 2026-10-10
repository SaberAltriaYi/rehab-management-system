package cn.iocoder.yudao.module.rehab.service.motion.report;

import cn.hutool.core.util.StrUtil;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 动作评估报告 PDF（PDFBox，中文字体加载策略与既有报告一致）。只渲染 content.sections，不含任何视频帧。
 */
@Component
public class MotionPdfRenderer {

    private static final float MARGIN = 48F;

    @Value("${yudao.rehab.pdf-font-path:${REHAB_PDF_FONT_PATH:}}")
    private String pdfFontPath;

    public MotionPdfRenderer() {
    }

    public MotionPdfRenderer(String pdfFontPath) {
        this.pdfFontPath = pdfFontPath;
    }

    @SuppressWarnings("unchecked")
    public byte[] render(Map<String, Object> content) throws IOException {
        List<Line> lines = new ArrayList<Line>();
        lines.add(new Line(StrUtil.toString(content.get("title")), 18F, 28F));
        lines.add(new Line("评估编号：" + content.get("assessment_ref") + "　报告版本：v" + content.get("version_no"), 10.5F, 16F));
        lines.add(new Line("", 10.5F, 10F));
        Object sections = content.get("sections");
        if (sections instanceof List) {
            int index = 1;
            for (Map<String, Object> section : (List<Map<String, Object>>) sections) {
                lines.add(new Line(index++ + ". " + section.get("title"), 13F, 22F));
                Object body = section.get("lines");
                if (body instanceof List) {
                    for (Object line : (List<Object>) body) {
                        List<String> wrapped = wrap(String.valueOf(line), 88);
                        if (wrapped.isEmpty()) {
                            lines.add(new Line("", 10.5F, 16F));
                        }
                        for (String w : wrapped) {
                            lines.add(new Line(w, 10.5F, 16F));
                        }
                    }
                }
                lines.add(new Line("", 10.5F, 10F));
            }
        }
        try (PDDocument document = new PDDocument(); FontResource font = loadFont(document)) {
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle(StrUtil.toString(content.get("title")));
            info.setCreator("rehab motion assessment");
            write(document, font.font, lines);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            document.save(bos);
            return bos.toByteArray();
        }
    }

    private void write(PDDocument document, PDFont font, List<Line> lines) throws IOException {
        float top = PDRectangle.A4.getHeight() - MARGIN;
        PDPageContentStream stream = null;
        float y = top;
        try {
            for (Line line : lines) {
                if (stream == null || y - line.leading < MARGIN) {
                    if (stream != null) {
                        stream.endText();
                        stream.close();
                    }
                    PDPage page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    stream.beginText();
                    stream.newLineAtOffset(MARGIN, top);
                    y = top;
                }
                stream.setFont(font, line.size);
                if (StrUtil.isNotEmpty(line.text)) {
                    stream.showText(encodable(font, line.text));
                }
                stream.newLineAtOffset(0, -line.leading);
                y -= line.leading;
            }
        } finally {
            if (stream != null) {
                stream.endText();
                stream.close();
            }
        }
    }

    /** 字体缺字时替换为 ?，避免整份 PDF 失败 */
    static String encodable(PDFont font, String text) {
        try {
            font.encode(text);
            return text;
        } catch (IOException | IllegalArgumentException ex) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                i += Character.charCount(cp);
                try {
                    font.encode(ch);
                    sb.append(ch);
                } catch (IOException | IllegalArgumentException e) {
                    sb.append('?');
                }
            }
            return sb.toString();
        }
    }

    static List<String> wrap(String text, int maxUnits) {
        if (StrUtil.isEmpty(text)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        int units = 0;
        for (int offset = 0; offset < text.length(); ) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            if (Character.isISOControl(cp)) {
                continue;
            }
            int w = cp <= 0x7F ? 1 : 2;
            if (units + w > maxUnits && line.length() > 0) {
                result.add(line.toString());
                line.setLength(0);
                units = 0;
            }
            line.appendCodePoint(cp);
            units += w;
        }
        if (line.length() > 0) {
            result.add(line.toString());
        }
        return result;
    }

    private FontResource loadFont(PDDocument document) throws IOException {
        List<String> candidates = new ArrayList<String>();
        if (StrUtil.isNotBlank(pdfFontPath)) {
            candidates.add(pdfFontPath);
        }
        candidates.add("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc");
        candidates.add("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc");
        candidates.add("/System/Library/Fonts/STHeiti Medium.ttc");
        candidates.add("/System/Library/Fonts/STHeiti Light.ttc");
        candidates.add("C:\\Windows\\Fonts\\msyh.ttc");
        candidates.add("C:\\Windows\\Fonts\\simhei.ttf");
        IOException last = null;
        for (String candidate : candidates) {
            File file = new File(candidate);
            if (!file.isFile()) {
                continue;
            }
            try {
                if (candidate.toLowerCase(Locale.ROOT).endsWith(".ttc")) {
                    TrueTypeCollection collection = new TrueTypeCollection(file);
                    for (String name : Arrays.asList("WenQuanYiZenHei", "WenQuanYiZenHeiMono", "NotoSansCJKsc-Regular",
                            "STHeitiSC-Medium", "STHeitiSC-Light", "MicrosoftYaHei", "SimHei")) {
                        TrueTypeFont ttf = collection.getFontByName(name);
                        if (ttf != null) {
                            return new FontResource(PDType0Font.load(document, ttf, true), collection);
                        }
                    }
                    collection.close();
                    continue;
                }
                return new FontResource(PDType0Font.load(document, file), null);
            } catch (IOException ex) {
                last = ex;
            }
        }
        throw last == null ? new IOException("未找到可用的中文 PDF 字体") : new IOException("中文 PDF 字体加载失败", last);
    }

    private static final class FontResource implements Closeable {
        private final PDFont font;
        private final TrueTypeCollection collection;

        private FontResource(PDFont font, TrueTypeCollection collection) {
            this.font = font;
            this.collection = collection;
        }

        @Override
        public void close() throws IOException {
            if (collection != null) {
                collection.close();
            }
        }
    }

    private static final class Line {
        private final String text;
        private final float size;
        private final float leading;

        private Line(String text, float size, float leading) {
            this.text = text;
            this.size = size;
            this.leading = leading;
        }
    }

}
