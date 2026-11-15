package cn.iocoder.yudao.module.rehab.service.motion;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.service.motion.workspace.MotionWorkspaceClient;
import cn.iocoder.yudao.module.rehab.service.motion.workspace.MotionWorkspaceService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Synthetic payloads, private loopback only. */
class MotionWorkspacePrivacyTest {
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;
    private static final String PRIVATE = "SYNTHETIC_WORKSPACE_PRIVATE";
    @BeforeEach void capture() {
        logger=(Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logs=new ListAppender<>(); logs.start(); logger.addAppender(logs);
    }
    @AfterEach void stop() { logger.detachAppender(logs); logs.stop(); }
    private Map<String,Object> response(String raw) {
        Map<String,Object> value=new HashMap<>(); value.put("snapshot_json",raw);
        value.put("sha256",DigestUtil.sha256Hex(raw)); value.put("revision",1); return value;
    }
    private void noPrivateDiagnostics(Throwable error) {
        assertFalse(error.toString().contains(PRIVATE)); assertNull(error.getCause());
        for (ILoggingEvent event : logs.list) {
            assertNotEquals(JsonUtils.class.getName(),event.getLoggerName(),"general parser emitted response diagnostics, possibly encoded bytes");
            assertFalse(event.getFormattedMessage().contains(PRIVATE),"private response reached logs");
            if (event.getThrowableProxy()!=null) assertFalse(ThrowableProxyUtil.asString(event.getThrowableProxy()).contains(PRIVATE));
        }
    }
    @Test void malformedSnapshotDoesNotLeakRawJsonOrParserCause() {
        RuntimeException error=assertThrows(RuntimeException.class,()->MotionWorkspaceService.validateSnapshot(
                response("{\"note\":\"" + PRIVATE + "\""),new RehabMotionAssessmentDO()));
        noPrivateDiagnostics(error);
    }
    @Test void malformedActualHttpResponseDoesNotLeakRawJson() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        byte[] body=("{\"note\":\"" + PRIVATE + "\"").getBytes(StandardCharsets.UTF_8);
        server.createContext("/synthetic",exchange->{
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);
            try (java.io.OutputStream stream=exchange.getResponseBody()) { stream.write(body); }
        });
        server.createContext("/failure",exchange->{
            exchange.sendResponseHeaders(503,body.length);
            try (java.io.OutputStream stream=exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            String url="http://127.0.0.1:" + server.getAddress().getPort();
            MotionWorkspaceClient client=new MotionWorkspaceClient(url,url,UUID.randomUUID().toString());
            RuntimeException error=assertThrows(RuntimeException.class,()->client.exchange("/synthetic",HttpMethod.GET,null));
            noPrivateDiagnostics(error);
            noPrivateDiagnostics(assertThrows(RuntimeException.class,()->client.exchange("/failure",HttpMethod.GET,null)));
        } finally { server.stop(0); }
    }
    @Test void malformedEnvelopeAndBindingUseControlledErrors() {
        for (String raw : Arrays.asList("null","[]","{\"binding\":123}","{\"binding\":\"" + PRIVATE + "\"}")) {
            IllegalStateException error=assertThrows(IllegalStateException.class,()->
                    MotionWorkspaceService.validateSnapshot(response(raw),new RehabMotionAssessmentDO()));
            noPrivateDiagnostics(error);
        }
        noPrivateDiagnostics(assertThrows(IllegalStateException.class,()->MotionWorkspaceService.validateSnapshot(null,new RehabMotionAssessmentDO())));
        Map<String,Object> wrong=new HashMap<>(); wrong.put("snapshot_json",123); wrong.put("sha256",123);
        noPrivateDiagnostics(assertThrows(IllegalStateException.class,()->MotionWorkspaceService.validateSnapshot(wrong,new RehabMotionAssessmentDO())));
    }
    private String validSnapshot() {
        return "{\"schema\":\"rehab-workspace-sync/1\",\"revision\":1,\"binding\":{\"tenant\":\"1\",\"assessment\":\"2\",\"patient\":\"3\",\"core_revision\":1},"
                + "\"observations\":[{\"value\":0,\"negative\":-2.5,\"unit\":\"deg\",\"note\":\"SYNTHETIC_KEEP\"}]}";
    }
    private RehabMotionAssessmentDO assessment() {
        RehabMotionAssessmentDO value=RehabMotionAssessmentDO.builder().id(2L).patientId(3L).inputRevision(1).build();
        value.setTenantId(1L); return value;
    }
    @Test void ambiguousDuplicateKeysAndTrailingDocumentsAreRejectedWithoutDiagnostics() {
        for (String raw : Arrays.asList(validSnapshot() + " {}",validSnapshot().replace("\"value\":0","\"value\":0,\"value\":1"))) {
            noPrivateDiagnostics(assertThrows(IllegalStateException.class,()->MotionWorkspaceService.validateSnapshot(response(raw),assessment())));
        }
    }
    @Test void validSnapshotKeepsOriginalJsonHashZeroNegativeUnitsAndNotes() {
        String raw=validSnapshot(); Map<String,Object> envelope=response(raw);
        Map<String,Object> parsed=MotionWorkspaceService.validateSnapshot(envelope,assessment());
        Map<?,?> item=(Map<?,?>)((List<?>)parsed.get("observations")).get(0);
        assertEquals(0,item.get("value")); assertEquals(-2.5,((Number)item.get("negative")).doubleValue());
        assertEquals("deg",item.get("unit")); assertEquals("SYNTHETIC_KEEP",item.get("note"));
        assertEquals(raw,envelope.get("snapshot_json")); assertEquals(DigestUtil.sha256Hex(raw),envelope.get("sha256"));
        assertTrue(logs.list.stream().noneMatch(event->JsonUtils.class.getName().equals(event.getLoggerName())));
    }
}
