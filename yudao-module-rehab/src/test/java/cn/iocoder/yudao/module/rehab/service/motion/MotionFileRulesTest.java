package cn.iocoder.yudao.module.rehab.service.motion;

import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 上传安全：路径白名单、目录穿越、可执行序列化格式、内容嗅探，以及前端过滤策略与服务端规则一致。
 */
class MotionFileRulesTest {

    private static final String W = "OpenCapData_abe79267-646f-436b-a19e-a9e1d8f32807/";

    @Test
    void classifiesRealOpenCapExportLayout() {
        assertEquals(RehabMotionConstants.FILE_MOT, MotionFileRules.classify(W + "OpenSimData/Kinematics/squat1.mot").getKind());
        assertEquals("OpenSimData/Kinematics/squat1.mot",
                MotionFileRules.classify(W + "OpenSimData/Kinematics/squat1.mot").getRelativePath());
        assertEquals("squat1", MotionFileRules.classify("MarkerData/squat1.trc").getTrialName());
        assertEquals(RehabMotionConstants.FILE_OSIM,
                MotionFileRules.classify(W + "OpenSimData/Model/LaiUhlrich2022_scaled.osim").getKind());
        assertEquals(RehabMotionConstants.FILE_METADATA, MotionFileRules.classify(W + "sessionMetadata.yaml").getKind());
        MotionFileRules.Classified video = MotionFileRules.classify(W + "Videos/Cam0/InputMedia/YBALANCE/YBALANCE.mov");
        assertEquals(RehabMotionConstants.FILE_VIDEO, video.getKind());
        assertEquals("Cam0", video.getCameraKey());
        assertEquals("video/quicktime", video.getContentType());
    }

    @Test
    void rejectsTraversalAbsoluteBackslashAndUnknownFiles() {
        for (String bad : Arrays.asList("../etc/passwd", "/abs/OpenSimData/Kinematics/a.mot",
                "OpenSimData/Kinematics/../../x.mot", "OpenSimData\\Kinematics\\a.mot", "C:/x.mot",
                "OpenSimData/Kinematics/a.mot\u0000.txt", "OpenSimData//Kinematics/a.mot",
                W + "OpenSimData/Kinematics/a.pickle", W + "MarkerData/a.pkl",
                W + "Videos/Cam0/cameraIntrinsicsExtrinsics.pickle", W + "Geometry/r_pelvis.vtp",
                W + "Videos/Cam0/OutputMedia_mmpose_0.8/squat1/squat1_syncd.mp4", W + ".DS_Store",
                "other/OpenSimData/Kinematics/a.mot", "")) {
            assertNull(MotionFileRules.classify(bad), bad);
        }
        StringBuilder longPath = new StringBuilder("OpenSimData/Kinematics/");
        for (int i = 0; i < 300; i++) {
            longPath.append('a');
        }
        assertNull(MotionFileRules.classify(longPath.append(".mot").toString()));
    }

    @Test
    void contentSniffingBlocksRenamedOrDangerousFiles() {
        assertTrue(MotionFileRules.contentLooksValid(RehabMotionConstants.FILE_MOT,
                "Coordinates\nversion=1\nnRows=2\ninDegrees=yes\nendheader\ntime\tpelvis_tilt\n".getBytes(StandardCharsets.UTF_8)));
        assertFalse(MotionFileRules.contentLooksValid(RehabMotionConstants.FILE_MOT, "time\tx\n".getBytes(StandardCharsets.UTF_8)));
        assertTrue(MotionFileRules.contentLooksValid(RehabMotionConstants.FILE_TRC,
                "PathFileType\t4\t(X/Y/Z)\tsquat.trc\n".getBytes(StandardCharsets.UTF_8)));
        assertFalse(MotionFileRules.contentLooksValid(RehabMotionConstants.FILE_OSIM,
                "<?xml?><!DOCTYPE x [<!ENTITY a \"b\">]><OpenSimDocument/>".getBytes(StandardCharsets.UTF_8)));
        assertTrue(MotionFileRules.contentLooksValid(RehabMotionConstants.FILE_OSIM,
                "<?xml version=\"1.0\"?><OpenSimDocument Version=\"40000\">".getBytes(StandardCharsets.UTF_8)));
        assertFalse(MotionFileRules.contentLooksValid(RehabMotionConstants.FILE_METADATA,
                "a: !!python/object/apply:os.system ['id']".getBytes(StandardCharsets.UTF_8)));
        assertFalse(MotionFileRules.contentLooksValid(RehabMotionConstants.FILE_MOT, new byte[]{'e', 0, 'n'}));
        byte[] mp4 = new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'q', 't', ' ', ' ', 0, 0, 0, 0};
        assertTrue(MotionFileRules.contentLooksValid(RehabMotionConstants.FILE_VIDEO, mp4));
        assertFalse(MotionFileRules.contentLooksValid(RehabMotionConstants.FILE_VIDEO,
                "MZ executable header!".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadPolicyForFrontendMatchesServerClassification() {
        Map<String, Object> policy = MotionFileRules.policy();
        List<Map<String, Object>> rules = (List<Map<String, Object>>) policy.get("rules");
        assertEquals(1, policy.get("concurrency"));
        List<String> samples = Arrays.asList(
                W + "OpenSimData/Kinematics/squat1.mot", "OpenSimData/Kinematics/squat1.mot",
                W + "MarkerData/squat1.trc", W + "OpenSimData/Model/LaiUhlrich2022_scaled.osim",
                W + "sessionMetadata.yaml", W + "Videos/Cam1/InputMedia/1legsquat/1legsquat.mov",
                W + "Videos/Cam1/InputMedia/1legsquat/1legsquat.MP4",
                W + "Videos/Cam0/cameraIntrinsicsExtrinsics.pickle", W + "Geometry/r_pelvis.vtp",
                W + "Videos/Cam0/OutputMedia_mmpose_0.8/squat1/squat1_syncd.mp4", W + "OpenSimData/Kinematics/a.pkl",
                W + "MarkerData/..trc", "../MarkerData/a.trc");
        for (String path : samples) {
            String serverKind = MotionFileRules.classify(path) == null ? null : MotionFileRules.classify(path).getKind();
            String policyKind = null;
            for (Map<String, Object> rule : rules) {
                if (Pattern.compile((String) rule.get("pattern")).matcher(path).matches()) {
                    policyKind = (String) rule.get("kind");
                    break;
                }
            }
            assertEquals(serverKind, policyKind, path);
        }
        for (Map<String, Object> rule : rules) {
            assertTrue(((Number) rule.get("maxBytes")).longValue() <= 16L * 1024 * 1024,
                    "单文件上限不得超过 Spring multipart 16MB");
        }
    }


    /**
     * 前端上传队列单测（tests/motion/uploadQueue.test.mjs）使用同一份策略快照；
     * 服务端规则变化时此测试失败，提醒同步前端快照，避免前后端对“哪些文件可上传”的判断不一致。
     */
    @Test
    @SuppressWarnings("unchecked")
    void uploadPolicySnapshotSharedWithFrontendIsInSync() throws Exception {
        java.io.InputStream in = MotionFileRulesTest.class.getResourceAsStream("/motion/upload-policy.json");
        assertNotNull(in);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        Map<String, Object> snapshot = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                new String(bos.toByteArray(), StandardCharsets.UTF_8), Map.class);
        Map<String, Object> live = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(MotionFileRules.policy()), Map.class);
        assertEquals(live, snapshot);
    }

}
