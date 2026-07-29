package cn.iocoder.yudao.module.rehab.service.assessment;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RehabSfmaBookProtocolTest {

    private final RehabSfmaBookProtocol protocol = new RehabSfmaBookProtocol();

    @Test
    void getProtocol_shouldExposeBookWorkflowAndRules() {
        Map<String, Object> result = protocol.getProtocol();
        assertEquals(RehabSfmaBookProtocol.PROTOCOL_ID, result.get("protocol_id"));
        assertEquals(RehabSfmaBookProtocol.PROTOCOL_VERSION, result.get("protocol_version"));
        assertEquals(8, ((List<?>) result.get("workflows")).size());
        assertTrue(String.valueOf(result.get("source")).contains("附录3"));
    }

    @Test
    void validate_shouldAcceptLegacyPayloadWithoutBookProtocol() {
        assertDoesNotThrow(() -> protocol.validate(Collections.singletonMap("top_tier", Collections.emptyMap())));
    }

    @Test
    void validate_shouldAcceptOrderedStepsAndPainStop() {
        Map<String, Object> first = step("cervical_supine_active_flexion", "DP", "completed");
        Map<String, Object> second = step("cervical_supine_passive_flexion", "", "stopped_due_to_pain");
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("status", "stopped_due_to_pain");
        workflow.put("steps", Arrays.asList(first, second));

        assertDoesNotThrow(() -> protocol.validate(payload("cervical", workflow)));
    }

    @Test
    void validate_shouldRejectResultAfterPain() {
        Map<String, Object> first = step("cervical_supine_active_flexion", "FP", "completed");
        Map<String, Object> second = step("cervical_supine_passive_flexion", "DN", "completed");
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("status", "stopped_due_to_pain");
        workflow.put("steps", Arrays.asList(first, second));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> protocol.validate(payload("cervical", workflow)));
        assertTrue(ex.getMessage().contains("疼痛终止后"));
    }

    @Test
    void validate_shouldRejectOutOfOrderStep() {
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("status", "in_progress");
        workflow.put("steps", Arrays.asList(
                step("cervical_supine_passive_flexion", "DN", "completed"),
                step("cervical_supine_active_flexion", "DN", "completed")
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> protocol.validate(payload("cervical", workflow)));
        assertTrue(ex.getMessage().contains("顺序"));
    }

    private static Map<String, Object> step(String code, String classification, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("test_code", code);
        result.put("classification", classification);
        result.put("left_classification", "");
        result.put("right_classification", "");
        result.put("status", status);
        return result;
    }

    private static Map<String, Object> payload(String workflowCode, Map<String, Object> workflow) {
        Map<String, Object> workflows = new LinkedHashMap<>();
        workflows.put(workflowCode, workflow);
        Map<String, Object> bookProtocol = new LinkedHashMap<>();
        bookProtocol.put("protocol_id", RehabSfmaBookProtocol.PROTOCOL_ID);
        bookProtocol.put("protocol_version", RehabSfmaBookProtocol.PROTOCOL_VERSION);
        bookProtocol.put("workflows", workflows);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("book_protocol", bookProtocol);
        return payload;
    }
}
