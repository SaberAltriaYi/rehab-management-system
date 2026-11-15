package cn.iocoder.yudao.module.rehab.enums;

/**
 * Rehab 角色编码常量
 */
public interface RehabRoleCodeConstants {

    String SUPER_ADMIN = "super_admin";
    // Legacy therapist role remains recognized for existing tenants.
    String REHAB_THERAPIST = "rehab_therapist";
    String TENANT_REHAB_THERAPIST = "tenant_rehab_therapist";
    String REHAB_CLINICAL_REVIEWER = "tenant_rehab_clinical_reviewer";
    String REHAB_CLERK = "rehab_clerk";

}
