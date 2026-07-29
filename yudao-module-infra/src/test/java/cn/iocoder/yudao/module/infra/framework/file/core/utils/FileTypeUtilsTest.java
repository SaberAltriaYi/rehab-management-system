package cn.iocoder.yudao.module.infra.framework.file.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FileTypeUtilsTest {

    @Test
    void shouldDetectContentAndFileNameTypes() {
        byte[] pdfHeader = "%PDF-1.7".getBytes();
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        assertEquals("application/pdf", FileTypeUtils.getMineType(pdfHeader));
        assertEquals("image/png", FileTypeUtils.getMineType(pngHeader, "avatar.bin"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                FileTypeUtils.getMineType("report.docx"));
    }

    @Test
    void shouldResolveOnlySupportedExtensions() {
        assertEquals(".pdf", FileTypeUtils.getExtension("application/pdf"));
        assertEquals(".xlsx", FileTypeUtils.getExtension(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertNull(FileTypeUtils.getExtension("application/x-unknown"));
    }

}
