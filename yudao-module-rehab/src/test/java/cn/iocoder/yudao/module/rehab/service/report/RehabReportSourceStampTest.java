package cn.iocoder.yudao.module.rehab.service.report;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. No real clinical inputs. */
class RehabReportSourceStampTest {
    @Test void storedDateAndHighPrecisionNumbersCanBeVerifiedWithoutLiveDatabase() {
        Map<String,Object> payload=new LinkedHashMap<>(), assessment=new LinkedHashMap<>();
        assessment.put("date",java.time.LocalDate.of(2026,10,27));
        assessment.put("measurement",new java.math.BigDecimal("1234567890.123456789012345"));
        assessment.put("zero",0); payload.put("assessment",assessment);
        payload.put("sourceSnapshot",RehabReportSourceStamp.of(payload,4L));
        String stored=cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(payload);
        assertTrue(RehabReportSourceStamp.matches(stored));
        assertFalse(RehabReportSourceStamp.matches(stored.replace("1234567890.123456789012345","1234567890.123456789012346")));
    }
    @Test void stableInputsIgnoreReportNumberTimeAndMapInsertionOrder() {
        Map<String,Object> a=new LinkedHashMap<>(), b=new LinkedHashMap<>();
        Map<String,Object> fields=new LinkedHashMap<>(); fields.put("zero",0); fields.put("negative",-2);
        Map<String,Object> reverse=new LinkedHashMap<>(); reverse.put("negative",-2); reverse.put("zero",0);
        a.put("assessment",fields); b.put("assessment",reverse); a.put("generatedAt","SYNTHETIC_TIME_A"); b.put("generatedAt","SYNTHETIC_TIME_B");
        a.put("reportNo","SYNTHETIC_A"); b.put("reportNo","SYNTHETIC_B");
        Map<String,Object> first=RehabReportSourceStamp.of(a,4L);
        assertEquals(first,RehabReportSourceStamp.of(b,4L));
        assertTrue(first.get("sha256").toString().matches("[a-f0-9]{64}"));
        fields.put("zero",1); assertNotEquals(first.get("sha256"),RehabReportSourceStamp.of(a,4L).get("sha256"));
        assertFalse(first.toString().contains("SYNTHETIC_TIME"));
    }
}
