package cn.iocoder.yudao.module.crm.service.department;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import cn.iocoder.yudao.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.crm.enums.ErrorCodeConstants.*;

/**
 * 自研部门分流：共享目录只返回最少字段，部门服务档案及备注始终按租户和部门查询。
 * JdbcTemplate 不经过 MyBatis 租户拦截器，所有 SQL 都必须显式绑定 tenant_id。
 * Copyright (c) 2026 杨玺龙。保留上游项目许可。
 */
@Service
public class CrmDepartmentService {

    @Resource private JdbcTemplate jdbc;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private CrmCustomerMapper customerMapper;

    private Long tenant() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private Long dept(Long userId) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null || user.getDeptId() == null) {
            throw exception(DEPARTMENT_REQUIRED);
        }
        return user.getDeptId();
    }

    public List<Workspace> workspaces() {
        return jdbc.query("SELECT dept_id,display_name,enabled FROM crm_department_workspace "
                        + "WHERE tenant_id=? ORDER BY dept_id", (rs, n) -> new Workspace(
                        rs.getLong(1), rs.getString(2), rs.getBoolean(3)), tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public void configureWorkspace(Long deptId, String displayName, boolean enabled) {
        deptApi.validateDeptList(Collections.singleton(deptId));
        if (displayName == null || displayName.trim().isEmpty() || displayName.length() > 100) {
            throw new IllegalArgumentException("工作台名称须为 1-100 字");
        }
        jdbc.update("INSERT INTO crm_department_workspace (tenant_id,dept_id,display_name,enabled) "
                        + "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE display_name=VALUES(display_name),enabled=VALUES(enabled)",
                tenant(), deptId, displayName.trim(), enabled);
    }

    private void requireWorkspace(Long deptId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM crm_department_workspace "
                + "WHERE tenant_id=? AND dept_id=? AND enabled=b'1'", Integer.class, tenant(), deptId);
        if (count == null || count == 0) {
            throw exception(DEPARTMENT_WORKSPACE_DISABLED);
        }
    }

    public PageResult<DirectoryItem> directory(String name, int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        String keyword = name == null ? "" : name.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String predicate = "c.tenant_id=? AND c.deleted=b'0' AND c.name LIKE ? ESCAPE '\\\\'";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer c WHERE " + predicate,
                Long.class, tenant(), "%" + keyword + "%");
        List<DirectoryItem> list = jdbc.query("SELECT c.id,c.name,dc.dept_id FROM crm_customer c "
                        + "LEFT JOIN crm_department_case dc ON dc.tenant_id=c.tenant_id AND dc.customer_id=c.id "
                        + "WHERE " + predicate + " ORDER BY c.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new DirectoryItem(rs.getLong(1), rs.getString(2), (Long) rs.getObject(3)),
                tenant(), "%" + keyword + "%", pageSize, offset);
        return new PageResult<>(list, total == null ? 0L : total);
    }

    public PageResult<CaseItem> cases(Long userId, int pageNo, int pageSize) {
        Long deptId = dept(userId);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM crm_department_case dc "
                + "JOIN crm_customer c ON c.id=dc.customer_id AND c.tenant_id=dc.tenant_id AND c.deleted=b'0' "
                + "WHERE dc.tenant_id=? AND dc.dept_id=?", Long.class, tenant(), deptId);
        List<CaseItem> list = jdbc.query("SELECT dc.id,dc.customer_id,c.name,dc.dept_id,dc.claimed_by,dc.claim_time,n.content "
                        + "FROM crm_department_case dc JOIN crm_customer c "
                        + "ON c.id=dc.customer_id AND c.tenant_id=dc.tenant_id AND c.deleted=b'0' "
                        + "LEFT JOIN crm_department_case_note n ON n.case_id=dc.id AND n.tenant_id=dc.tenant_id AND n.dept_id=dc.dept_id "
                        + "WHERE dc.tenant_id=? AND dc.dept_id=? ORDER BY dc.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new CaseItem(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4),
                        rs.getLong(5), rs.getTimestamp(6).toLocalDateTime(), rs.getString(7)),
                tenant(), deptId, pageSize, (pageNo - 1) * pageSize);
        return new PageResult<>(list, total == null ? 0L : total);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long claim(Long customerId, Long userId) {
        Long deptId = dept(userId);
        requireWorkspace(deptId);
        if (customerMapper.selectById(customerId) == null) {
            throw exception(CUSTOMER_NOT_EXISTS);
        }
        boolean newlyClaimed = false;
        try {
            jdbc.update("INSERT INTO crm_department_case (tenant_id,customer_id,dept_id,claimed_by) VALUES (?,?,?,?)",
                    tenant(), customerId, deptId, userId);
            newlyClaimed = true;
        } catch (DuplicateKeyException ignored) {
            // 唯一键使并发认领只有一个部门成功；同部门重试为幂等。
        }
        List<Long> ids = jdbc.query("SELECT id FROM crm_department_case WHERE tenant_id=? AND customer_id=? AND dept_id=?",
                (rs, n) -> rs.getLong(1), tenant(), customerId, deptId);
        if (ids.isEmpty()) {
            throw exception(DEPARTMENT_CASE_CLAIMED);
        }
        if (newlyClaimed) {
            assignCustomerOwner(customerId, userId);
        }
        return ids.get(0);
    }

    /** 新建客户在已启用的工作台自动归属创建者部门，避免短暂落入可被其他部门认领的状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void registerCreatedCustomer(Long customerId, Long ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        AdminUserRespDTO owner = adminUserApi.getUser(ownerUserId);
        if (owner == null || owner.getDeptId() == null) {
            return; // 无明确部门的历史兼容账号仍可创建共享目录客户。
        }
        Long deptId = owner.getDeptId();
        Integer enabled = jdbc.queryForObject("SELECT COUNT(*) FROM crm_department_workspace "
                + "WHERE tenant_id=? AND dept_id=? AND enabled=b'1'", Integer.class, tenant(), deptId);
        if (enabled != null && enabled > 0) {
            jdbc.update("INSERT IGNORE INTO crm_department_case (tenant_id,customer_id,dept_id,claimed_by) "
                    + "VALUES (?,?,?,?)", tenant(), customerId, deptId, ownerUserId);
        }
    }

    private void assignCustomerOwner(Long customerId, Long userId) {
        jdbc.update("UPDATE crm_customer SET owner_user_id=?,owner_time=NOW() "
                        + "WHERE tenant_id=? AND id=? AND deleted=b'0'", userId, tenant(), customerId);
        jdbc.update("UPDATE crm_permission SET deleted=b'1' "
                        + "WHERE tenant_id=? AND biz_type=2 AND biz_id=? AND level=1", tenant(), customerId);
        List<Long> ownPermission = jdbc.query("SELECT id FROM crm_permission WHERE tenant_id=? "
                        + "AND biz_type=2 AND biz_id=? AND user_id=? ORDER BY id LIMIT 1",
                (rs, n) -> rs.getLong(1), tenant(), customerId, userId);
        if (ownPermission.isEmpty()) {
            jdbc.update("INSERT INTO crm_permission (tenant_id,biz_type,biz_id,user_id,level) VALUES (?,2,?,?,1)",
                    tenant(), customerId, userId);
        } else {
            jdbc.update("UPDATE crm_permission SET level=1,deleted=b'0' WHERE tenant_id=? AND id=?",
                    tenant(), ownPermission.get(0));
        }
    }

    public void assertCustomerVisible(Long customerId, Long userId) {
        List<Long> ownerDepts = jdbc.query("SELECT dept_id FROM crm_department_case WHERE tenant_id=? AND customer_id=?",
                (rs, n) -> rs.getLong(1), tenant(), customerId);
        if (!ownerDepts.isEmpty() && !Objects.equals(ownerDepts.get(0), dept(userId))) {
            throw exception(DEPARTMENT_CASE_DENIED);
        }
    }

    public boolean isCustomerInOwnDept(Long customerId, Long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM crm_department_case WHERE "
                        + "tenant_id=? AND customer_id=? AND dept_id=?", Integer.class,
                tenant(), customerId, dept(userId));
        return count != null && count > 0;
    }

    public void assertBizVisible(Integer bizType, Long bizId, Long userId) {
        if (CrmBizTypeEnum.CRM_CUSTOMER.getType().equals(bizType)) {
            assertCustomerVisible(bizId, userId);
            return;
        }
        String sql;
        if (CrmBizTypeEnum.CRM_CLUE.getType().equals(bizType)) {
            sql = "SELECT customer_id FROM crm_clue WHERE tenant_id=? AND id=? AND deleted=b'0'";
        } else if (CrmBizTypeEnum.CRM_CONTACT.getType().equals(bizType)) {
            sql = "SELECT customer_id FROM crm_contact WHERE tenant_id=? AND id=? AND deleted=b'0'";
        } else if (CrmBizTypeEnum.CRM_BUSINESS.getType().equals(bizType)) {
            sql = "SELECT customer_id FROM crm_business WHERE tenant_id=? AND id=? AND deleted=b'0'";
        } else if (CrmBizTypeEnum.CRM_CONTRACT.getType().equals(bizType)) {
            sql = "SELECT customer_id FROM crm_contract WHERE tenant_id=? AND id=? AND deleted=b'0'";
        } else if (CrmBizTypeEnum.CRM_RECEIVABLE.getType().equals(bizType)) {
            sql = "SELECT c.customer_id FROM crm_receivable r JOIN crm_contract c ON c.id=r.contract_id "
                    + "AND c.tenant_id=r.tenant_id WHERE r.tenant_id=? AND r.id=? AND r.deleted=b'0'";
        } else if (CrmBizTypeEnum.CRM_RECEIVABLE_PLAN.getType().equals(bizType)) {
            sql = "SELECT c.customer_id FROM crm_receivable_plan r JOIN crm_contract c ON c.id=r.contract_id "
                    + "AND c.tenant_id=r.tenant_id WHERE r.tenant_id=? AND r.id=? AND r.deleted=b'0'";
        } else {
            return; // 产品为全局目录，不属于客户私有服务档案。
        }
        List<Long> customerIds = jdbc.query(sql, (rs, n) -> (Long) rs.getObject(1), tenant(), bizId);
        if (!customerIds.isEmpty() && customerIds.get(0) != null) {
            assertCustomerVisible(customerIds.get(0), userId);
        }
    }

    public void assertLegacyOwnerTransferWithinDepartment(Long customerId, Long newOwnerUserId) {
        List<Long> depts = jdbc.query("SELECT dept_id FROM crm_department_case WHERE tenant_id=? AND customer_id=?",
                (rs, n) -> rs.getLong(1), tenant(), customerId);
        if (!depts.isEmpty() && !Objects.equals(depts.get(0), dept(newOwnerUserId))) {
            throw exception(DEPARTMENT_TRANSFER_INVALID);
        }
    }

    private Long requireOwnCase(Long caseId, Long userId) {
        Long deptId = dept(userId);
        List<Long> ids = jdbc.query("SELECT id FROM crm_department_case WHERE tenant_id=? AND id=? AND dept_id=?",
                (rs, n) -> rs.getLong(1), tenant(), caseId, deptId);
        if (ids.isEmpty()) {
            throw exception(DEPARTMENT_CASE_DENIED);
        }
        return deptId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveNote(Long caseId, String content, Long userId) {
        Long deptId = requireOwnCase(caseId, userId);
        if (content == null || content.length() > 4000) {
            throw new IllegalArgumentException("部门记录不得为空或超过 4000 字");
        }
        jdbc.update("INSERT INTO crm_department_case_note (tenant_id,case_id,dept_id,content,updated_by) "
                        + "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE content=VALUES(content),updated_by=VALUES(updated_by)",
                tenant(), caseId, deptId, content, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long requestTransfer(Long caseId, Long toDeptId, String reason, Long userId) {
        Long fromDeptId = requireOwnCase(caseId, userId);
        if (Objects.equals(fromDeptId, toDeptId)) {
            throw exception(DEPARTMENT_TRANSFER_INVALID);
        }
        requireWorkspace(toDeptId);
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM crm_department_transfer WHERE "
                + "tenant_id=? AND case_id=? AND status='PENDING'", Integer.class, tenant(), caseId);
        if (pending != null && pending > 0) {
            throw exception(DEPARTMENT_TRANSFER_INVALID);
        }
        if (reason != null && reason.length() > 200) {
            throw new IllegalArgumentException("转接说明最多 200 字，不应包含患者敏感记录");
        }
        try {
            jdbc.update("INSERT INTO crm_department_transfer "
                            + "(tenant_id,case_id,from_dept_id,to_dept_id,requested_by,status,reason) VALUES (?,?,?,?,?,'PENDING',?)",
                    tenant(), caseId, fromDeptId, toDeptId, userId, reason);
        } catch (DuplicateKeyException ex) {
            throw exception(DEPARTMENT_TRANSFER_INVALID);
        }
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public List<TransferItem> incoming(Long userId) {
        Long deptId = dept(userId);
        requireWorkspace(deptId);
        return jdbc.query("SELECT t.id,t.case_id,t.from_dept_id,t.to_dept_id,t.reason,t.create_time,c.name "
                        + "FROM crm_department_transfer t JOIN crm_department_case dc "
                        + "ON dc.id=t.case_id AND dc.tenant_id=t.tenant_id AND dc.dept_id=t.from_dept_id "
                        + "JOIN crm_customer c ON c.id=dc.customer_id AND c.tenant_id=dc.tenant_id AND c.deleted=b'0' "
                        + "WHERE t.tenant_id=? AND t.to_dept_id=? AND t.status='PENDING' ORDER BY t.id DESC",
                (rs, n) -> new TransferItem(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                        rs.getString(5), rs.getTimestamp(6).toLocalDateTime(), rs.getString(7)), tenant(), deptId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resolveTransfer(Long transferId, boolean accept, Long userId) {
        Long deptId = dept(userId);
        List<TransferItem> transfers = jdbc.query("SELECT t.id,t.case_id,t.from_dept_id,t.to_dept_id,t.reason,t.create_time,c.name "
                        + "FROM crm_department_transfer t JOIN crm_department_case dc "
                        + "ON dc.id=t.case_id AND dc.tenant_id=t.tenant_id "
                        + "JOIN crm_customer c ON c.id=dc.customer_id AND c.tenant_id=dc.tenant_id "
                        + "WHERE t.tenant_id=? AND t.id=? AND t.to_dept_id=? AND t.status='PENDING' FOR UPDATE",
                (rs, n) -> new TransferItem(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                        rs.getString(5), rs.getTimestamp(6).toLocalDateTime(), rs.getString(7)),
                tenant(), transferId, deptId);
        if (transfers.isEmpty()) {
            throw exception(DEPARTMENT_TRANSFER_INVALID);
        }
        TransferItem transfer = transfers.get(0);
        if (accept) {
            requireWorkspace(deptId);
            int changed = jdbc.update("UPDATE crm_department_case SET dept_id=?,claimed_by=? "
                            + "WHERE tenant_id=? AND id=? AND dept_id=?",
                    deptId, userId, tenant(), transfer.getCaseId(), transfer.getFromDeptId());
            if (changed != 1) {
                throw exception(DEPARTMENT_TRANSFER_INVALID);
            }
            Long customerId = jdbc.queryForObject("SELECT customer_id FROM crm_department_case WHERE tenant_id=? AND id=?",
                    Long.class, tenant(), transfer.getCaseId());
            assignCustomerOwner(customerId, userId);
        }
        jdbc.update("UPDATE crm_department_transfer SET status=?,resolved_by=?,resolve_time=NOW() "
                        + "WHERE tenant_id=? AND id=? AND status='PENDING'",
                accept ? "ACCEPTED" : "REJECTED", userId, tenant(), transferId);
    }

    @Data @NoArgsConstructor @AllArgsConstructor public static class Workspace {
        private Long deptId;
        private String displayName;
        private Boolean enabled;
    }
    @Data @NoArgsConstructor @AllArgsConstructor public static class DirectoryItem {
        private Long customerId;
        private String name;
        private Long claimedDeptId;
    }
    @Data @NoArgsConstructor @AllArgsConstructor public static class CaseItem {
        private Long id;
        private Long customerId;
        private String customerName;
        private Long deptId;
        private Long claimedBy;
        private LocalDateTime claimTime;
        private String privateNote;
    }
    @Data @NoArgsConstructor @AllArgsConstructor public static class TransferItem {
        private Long id;
        private Long caseId;
        private Long fromDeptId;
        private Long toDeptId;
        private String reason;
        private LocalDateTime createTime;
        private String customerName;
    }
}
