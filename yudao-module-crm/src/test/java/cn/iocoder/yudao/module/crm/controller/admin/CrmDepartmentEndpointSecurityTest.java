package cn.iocoder.yudao.module.crm.controller.admin;

import cn.iocoder.yudao.module.crm.controller.admin.business.CrmBusinessController;
import cn.iocoder.yudao.module.crm.controller.admin.contact.CrmContactController;
import cn.iocoder.yudao.module.crm.controller.admin.contract.CrmContractController;
import cn.iocoder.yudao.module.crm.controller.admin.customer.CrmCustomerController;
import cn.iocoder.yudao.module.crm.controller.admin.receivable.CrmReceivableController;
import cn.iocoder.yudao.module.crm.controller.admin.receivable.CrmReceivablePlanController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 防止关联分页或客户选择器重新开放为仅登录可访问。 */
class CrmDepartmentEndpointSecurityTest {
    @Test void customerRelatedPagesAndSelectorsRequirePermission() {
        for (Class<?> controller : Arrays.asList(CrmBusinessController.class, CrmContactController.class,
                CrmContractController.class, CrmReceivableController.class, CrmReceivablePlanController.class,
                CrmCustomerController.class)) {
            for (Method method : controller.getDeclaredMethods()) {
                GetMapping mapping = method.getAnnotation(GetMapping.class);
                if (mapping == null) continue;
                boolean sensitive = Arrays.stream(mapping.value()).anyMatch(path ->
                        path.startsWith("/page-by-") || path.equals("/simple-list") || path.equals("/simple-search"));
                if (sensitive) {
                    assertNotNull(method.getAnnotation(PreAuthorize.class),
                            controller.getSimpleName() + "." + method.getName() + " 缺少权限检查");
                }
            }
        }
    }
}
