package cn.iocoder.yudao.module.rehab.service.motion;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.service.motion.workspace.MotionWorkspaceClient;
import cn.iocoder.yudao.module.rehab.service.motion.workspace.MotionWorkspaceService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit opt-in, actual Java client -> disposable Python service; no real Core database. */
class MotionWorkspaceHttpInteropTest {
    private String base;

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(
            String method, String path, Map<String, Object> data, String token) throws Exception {
        return call(method, path, data, token, 200);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(
            String method, String path, Map<String, Object> data, String token, int expected) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(1000);
        c.setReadTimeout(10000);
        c.setInstanceFollowRedirects(false);
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        if (data != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.getOutputStream()
                    .write(JsonUtils.toJsonString(data).getBytes(StandardCharsets.UTF_8));
        }
        try {
            assertEquals(expected, c.getResponseCode(), "local synthetic HTTP contract failed");
            if (expected != 200) return Collections.emptyMap();
            try (InputStream in = c.getInputStream()) {
                return JsonUtils.parseObject(in.readAllBytes(), Map.class);
            }
        } finally {
            c.disconnect();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void realServiceSingleUseEntryConfirmationAndSnapshotHashInterop() throws Exception {
        String root = System.getenv("REHAB_WORKSPACE_TEST_REPO");
        assumeTrue(
                root != null && !root.isEmpty(), "explicit isolated Python checkout not supplied");
        Path repo = Paths.get(root).toRealPath();
        assertTrue(Files.isRegularFile(repo.resolve("rehab_reporter/assessment_workspace/app.py")));
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        base = "http://127.0.0.1:" + port;
        Path data = Files.createTempDirectory("rehab-java-python-interop-");
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        ProcessBuilder builder =
                new ProcessBuilder(
                        repo.resolve(".venv/bin/python").toString(),
                        "-m",
                        "rehab_reporter.assessment_workspace",
                        "serve",
                        "--data-dir",
                        data.toString(),
                        "--port",
                        String.valueOf(port));
        builder.directory(repo.toFile());
        builder.environment().put("REHAB_CORE_BRIDGE_TOKEN", secret);
        builder.environment().put("OPENAI_ENABLE_AI_ANALYSIS", "false");
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        try {
            boolean ready = false;
            for (int i = 0; i < 100; i++) {
                try {
                    call("GET", "/api/health", null, null);
                    ready = true;
                    break;
                } catch (ConnectException ex) {
                    Thread.sleep(100);
                }
            }
            assertTrue(ready, "isolated Python service did not become ready");
            MotionWorkspaceClient client = new MotionWorkspaceClient(base, base, secret);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tenant", "1");
            payload.put("assessment", "2");
            payload.put("patient", "3");
            payload.put("episode", null);
            payload.put("core_revision", 1);
            payload.put("user", "4");
            payload.put("rights", Arrays.asList("view", "edit", "review", "sync"));
            payload.put("population", "adult_rehab");
            payload.put("video_consent", false);
            Map<String, Object> launch =
                    client.exchange("/internal/core/launch", HttpMethod.POST, payload);
            assertTrue(
                    client.entryUrl((String) launch.get("ticket")).startsWith(base + "/#launch="));
            Map<String, Object> exchanged =
                    call(
                            "POST",
                            "/api/core/exchange",
                            Collections.singletonMap("ticket", launch.get("ticket")),
                            null);
            String token = (String) exchanged.get("token"),
                    aid = (String) exchanged.get("assessment_id"),
                    path = "/api/assessments/" + aid;
            // These are dedicated synthetic service claims, not a real Core login.
            call("POST", "/api/core/exchange", Collections.singletonMap("ticket", launch.get("ticket")), null, 401);
            call("GET", path, null, null, 401);
            call("GET", path, null, "SYNTHETIC_INVALID_SESSION", 401);
            call("POST", "/internal/core/launch", payload, "SYNTHETIC_INVALID_MACHINE", 401);
            call("POST", path + "/core-sync", Collections.singletonMap("expected_version", 1), token, 409);
            Map<String,Object> readonly = new LinkedHashMap<>(payload);
            readonly.put("rights", Collections.singletonList("view"));
            Map<String,Object> readEntry = client.exchange("/internal/core/launch", HttpMethod.POST, readonly);
            Map<String,Object> readSession = call("POST", "/api/core/exchange", Collections.singletonMap("ticket", readEntry.get("ticket")), null);
            String readToken = (String) readSession.get("token");
            call("GET", path, null, readToken);
            Map<String,Object> forbiddenSource = new HashMap<>();
            forbiddenSource.put("expected_version", 1); forbiddenSource.put("title", "SYNTHETIC_DENIED"); forbiddenSource.put("kind", "onsite");
            call("POST", path + "/sources", forbiddenSource, readToken, 403);
            Map<String,Object> other = new LinkedHashMap<>(payload); other.put("tenant", "9");
            Map<String,Object> otherEntry = client.exchange("/internal/core/launch", HttpMethod.POST, other);
            Map<String,Object> otherSession = call("POST", "/api/core/exchange", Collections.singletonMap("ticket", otherEntry.get("ticket")), null);
            call("GET", "/api/assessments/" + otherSession.get("assessment_id"), null, token, 404);
            call("GET", path, null, (String) otherSession.get("token"), 404);
            for (Map<String,Object> changed : Arrays.asList(
                    Collections.<String,Object>singletonMap("patient", "44"), Collections.<String,Object>singletonMap("episode", "9"),
                    Collections.<String,Object>singletonMap("core_revision", 2))) {
                Map<String,Object> wrong = new LinkedHashMap<>(payload); wrong.putAll(changed);
                call("POST", "/internal/core/launch", wrong, secret, 409);
            }
            Map<String, Object> source = new HashMap<>();
            source.put("expected_version", 1);
            source.put("title", "synthetic onsite evidence");
            source.put("kind", "onsite");
            Map<String, Object> doc = call("POST", path + "/sources", source, token);
            String sid = (String) ((Map<?, ?>) ((List<?>) doc.get("sources")).get(0)).get("id");
            Map<String, Object> observation = new HashMap<>();
            observation.put("expected_version", doc.get("version"));
            observation.put("module", "opencap");
            observation.put("label", "synthetic coordinate");
            observation.put("state", "measured");
            observation.put("value", 0);
            observation.put("unit", "deg");
            observation.put("source_id", sid);
            doc = call("POST", path + "/observations", observation, token);
            Map<String, Object> consent = new HashMap<>();
            consent.put("expected_version", doc.get("version"));
            consent.put("text", true);
            doc = call("PUT", path + "/consent", consent, token);
            doc =
                    call(
                            "POST",
                            path + "/confirm",
                            Collections.singletonMap("expected_version", doc.get("version")),
                            token);
            Map<String,Object> receipt = call(
                    "POST",
                    path + "/core-sync",
                    Collections.singletonMap("expected_version", doc.get("version")),
                    token);
            assertEquals(receipt,call("POST", path + "/core-sync", Collections.singletonMap("expected_version", doc.get("version")), token));
            Map<String, Object> result =
                    client.exchange(
                            "/internal/core/result?tenant=1&assessment=2", HttpMethod.GET, null);
            RehabMotionAssessmentDO a =
                    RehabMotionAssessmentDO.builder().id(2L).patientId(3L).inputRevision(1).build();
            a.setTenantId(1L);
            Map<String, Object> snapshot = MotionWorkspaceService.validateSnapshot(result, a);
            assertEquals(
                    0,
                    ((Number)
                                    ((Map<?, ?>) ((List<?>) snapshot.get("observations")).get(0))
                                            .get("value"))
                            .intValue());
            assertEquals(aid, snapshot.get("assessment_id"));
            a.setInputRevision(2);
            assertThrows(IllegalStateException.class, () -> MotionWorkspaceService.validateSnapshot(result, a));
            a.setInputRevision(1);
            Map<String,Object> changedSource = new HashMap<>();
            changedSource.put("expected_version", doc.get("version")); changedSource.put("title", "SYNTHETIC_NEW_SOURCE"); changedSource.put("kind", "onsite");
            call("POST", path + "/sources", changedSource, token);
            call("GET", "/internal/core/result?tenant=1&assessment=2", null, secret, 409);
        } finally {
            process.destroy();
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
