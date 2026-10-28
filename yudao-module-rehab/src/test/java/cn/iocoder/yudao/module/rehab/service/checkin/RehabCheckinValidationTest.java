package cn.iocoder.yudao.module.rehab.service.checkin;

import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.RehabDailyCheckinCreateReqVO;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.AppPatientCheckinCreateReqVO;
import org.junit.jupiter.api.Test;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Synthetic request validation only. */
class RehabCheckinValidationTest {
    @Test void adminRequestRejectsNullListElement() {
        RehabDailyCheckinCreateReqVO request = new RehabDailyCheckinCreateReqVO();
        request.setTaskExecutions(Collections.singletonList(null));
        assertNullElementRejected(request);
    }

    @Test void patientRequestRejectsNullListElement() {
        AppPatientCheckinCreateReqVO request = new AppPatientCheckinCreateReqVO();
        request.setTaskExecutions(Collections.singletonList(null));
        assertNullElementRejected(request);
    }

    private void assertNullElementRejected(Object request) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).stream().anyMatch(violation ->
                    violation.getPropertyPath().toString().startsWith("taskExecutions[0]")),
                    "null execution element must be rejected by request validation");
        }
    }
}
