package cn.iocoder.yudao.module.rehab.service.motion;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 动作评估文件存储：${yudao.rehab.storage-path}/motion/{tenantId}/{assessmentId}/{kind}/{uuid}.{ext}。
 * 数据库只保存相对路径；读取时做规范化路径校验，防止越界访问。
 */
@Component
public class MotionStorage {

    private static final Pattern SAFE_EXT = Pattern.compile("^[a-z0-9]{1,8}$");
    private static final Pattern SAFE_KIND = Pattern.compile("^[a-z]{1,16}$");

    @Value("${yudao.rehab.storage-path:./data/rehab}")
    private String storagePath;

    public MotionStorage() {
    }

    /** 测试用 */
    public MotionStorage(String storagePath) {
        this.storagePath = storagePath;
    }

    public static final class Stored {
        public final String relativePath;
        public final String sha256;
        public final long size;

        Stored(String relativePath, String sha256, long size) {
            this.relativePath = relativePath;
            this.sha256 = sha256;
            this.size = size;
        }
    }

    public Stored save(Long tenantId, Long assessmentId, String kind, String ext, byte[] data) throws IOException {
        if (!SAFE_KIND.matcher(kind).matches() || !SAFE_EXT.matcher(ext).matches()) {
            throw new IOException("unsafe kind/ext");
        }
        String relative = "motion/" + (tenantId == null ? 0L : tenantId) + "/" + assessmentId + "/" + kind + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        File target = resolve(relative);
        FileUtil.mkParentDirs(target);
        File tmp = new File(target.getParentFile(), target.getName() + ".part");
        try {
            Files.write(tmp.toPath(), data);
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            FileUtil.del(tmp);
            FileUtil.del(target);
            throw ex;
        }
        return new Stored(relative, DigestUtil.sha256Hex(data), data.length);
    }

    public byte[] read(String relativePath) throws IOException {
        File file = resolve(relativePath);
        if (!file.isFile()) {
            throw new IOException("not found");
        }
        return Files.readAllBytes(file.toPath());
    }

    public File file(String relativePath) throws IOException {
        File file = resolve(relativePath);
        if (!file.isFile()) {
            throw new IOException("not found");
        }
        return file;
    }

    public void deleteQuietly(String relativePath) {
        try {
            FileUtil.del(resolve(relativePath));
        } catch (IOException ignored) {
            // 路径非法时不做任何删除
        }
    }

    File resolve(String relativePath) throws IOException {
        if (relativePath == null || !relativePath.startsWith("motion/") || relativePath.contains("..")
                || relativePath.contains("\\")) {
            throw new IOException("unsafe path");
        }
        File root = new File(storagePath).getCanonicalFile();
        File target = new File(root, relativePath).getCanonicalFile();
        if (!target.getPath().startsWith(root.getPath() + File.separator + "motion" + File.separator)) {
            throw new IOException("path escapes storage root");
        }
        return target;
    }

}
