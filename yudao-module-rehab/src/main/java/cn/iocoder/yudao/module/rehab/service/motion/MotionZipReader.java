package cn.iocoder.yudao.module.rehab.service.motion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 安全读取引擎返回的 OpenCap 下载包：条目数、单条目与总解压字节按实际读取量限制（不信任头部声明），
 * 只保留白名单路径，其余条目（如索引 JSON）跳过。
 */
public final class MotionZipReader {

    public static final int MAX_ENTRIES = 2000;
    public static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;

    private MotionZipReader() {
    }

    public static final class Entry {
        public final MotionFileRules.Classified classified;
        public final byte[] data;

        Entry(MotionFileRules.Classified classified, byte[] data) {
            this.classified = classified;
            this.data = data;
        }
    }

    public static List<Entry> read(byte[] zip, boolean includeVideos) throws IOException {
        List<Entry> out = new ArrayList<Entry>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zis.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) {
                    throw new IOException("too many entries");
                }
                if (e.isDirectory()) {
                    continue;
                }
                MotionFileRules.Classified c = MotionFileRules.classify(e.getName());
                if (c == null || (!includeVideos && "video".equals(c.getKind()))) {
                    continue;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int n;
                long size = 0;
                while ((n = zis.read(buf)) > 0) {
                    size += n;
                    total += n;
                    if (size > c.getMaxBytes()) {
                        throw new IOException("entry too large");
                    }
                    if (total > MAX_TOTAL_BYTES) {
                        throw new IOException("archive too large");
                    }
                    bos.write(buf, 0, n);
                }
                byte[] data = bos.toByteArray();
                byte[] head = data.length > MotionFileRules.sniffLength(c.getKind())
                        ? java.util.Arrays.copyOf(data, MotionFileRules.sniffLength(c.getKind())) : data;
                if (!MotionFileRules.contentLooksValid(c.getKind(), head)) {
                    throw new IOException("content check failed");
                }
                out.add(new Entry(c, data));
            }
        }
        return out;
    }

}
