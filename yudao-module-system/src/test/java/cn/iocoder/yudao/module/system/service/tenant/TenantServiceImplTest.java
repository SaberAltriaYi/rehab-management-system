package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.role.RoleSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.TenantMapper;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import cn.iocoder.yudao.module.system.enums.permission.RoleTypeEnum;
import cn.iocoder.yudao.module.system.service.permission.MenuService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.permission.RoleService;
import cn.iocoder.yudao.module.system.service.tenant.handler.TenantInfoHandler;
import cn.iocoder.yudao.module.system.service.tenant.handler.TenantMenuHandler;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.util.collection.SetUtils.asSet;
import static cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils.buildBetweenTime;
import static cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils.buildTime;
import static cn.iocoder.yudao.framework.common.util.object.ObjectUtils.cloneIgnoreId;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.*;
import static cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO.PACKAGE_ID_SYSTEM;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link TenantServiceImpl} 的单元测试类
 *
 * @author 芋道源码
 */
@Import(TenantServiceImpl.class)
public class TenantServiceImplTest extends BaseDbUnitTest {

    @Resource
    private TenantServiceImpl tenantService;

    @Resource
    private TenantMapper tenantMapper;

    @MockBean
    private TenantProperties tenantProperties;
    @MockBean
    private TenantPackageService tenantPackageService;
    @MockBean
    private AdminUserService userService;
    @MockBean
    private RoleService roleService;
    @MockBean
    private MenuService menuService;
    @MockBean
    private PermissionService permissionService;

    @BeforeEach
    public void setUp() {
        // 清理租户上下文
        TenantContextHolder.clear();
    }

    @Test
    public void testGetTenantIdList() {
        // mock 数据
        TenantDO tenant = randomPojo(TenantDO.class, o -> o.setId(1L));
        tenantMapper.insert(tenant);

        // 调用，并断言业务异常
        List<Long> result = tenantService.getTenantIdList();
        assertEquals(Collections.singletonList(1L), result);
    }

    @Test
    public void testValidTenant_notExists() {
        assertServiceException(() -> tenantService.validTenant(randomLongId()), TENANT_NOT_EXISTS);
    }

    @Test
    public void testValidTenant_disable() {
        // mock 数据
        TenantDO tenant = randomPojo(TenantDO.class, o -> o.setId(1L).setStatus(CommonStatusEnum.DISABLE.getStatus()));
        tenantMapper.insert(tenant);

        // 调用，并断言业务异常
        assertServiceException(() -> tenantService.validTenant(1L), TENANT_DISABLE, tenant.getName());
    }

    @Test
    public void testValidTenant_expired() {
        // mock 数据
        TenantDO tenant = randomPojo(TenantDO.class, o -> o.setId(1L).setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setExpireTime(buildTime(2020, 2, 2)));
        tenantMapper.insert(tenant);

        // 调用，并断言业务异常
        assertServiceException(() -> tenantService.validTenant(1L), TENANT_EXPIRE, tenant.getName());
    }

    @Test
    public void testValidTenant_success() {
        // mock 数据
        TenantDO tenant = randomPojo(TenantDO.class, o -> o.setId(1L).setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setExpireTime(LocalDateTime.now().plusDays(1)));
        tenantMapper.insert(tenant);

        // 调用，并断言业务异常
        tenantService.validTenant(1L);
    }

    @Test
    public void testCreateTenant() {
        // mock 套餐 100L：菜单集合是功能上限，不直接成为管理员权限集合
        List<MenuDO> menus = roleTemplateMenus();
        Set<Long> packageMenuIds = menus.stream().map(MenuDO::getId).collect(Collectors.toSet());
        TenantPackageDO tenantPackage = randomPojo(TenantPackageDO.class,
                o -> o.setId(100L).setMenuIds(packageMenuIds));
        when(tenantPackageService.validTenantPackage(eq(100L))).thenReturn(tenantPackage);
        when(menuService.getMenuList()).thenReturn(menus);
        // Three built-in roles are created; only the tenant administrator is assigned to the bootstrap user.
        when(roleService.createRole(any(RoleSaveReqVO.class), eq(RoleTypeEnum.SYSTEM.getType())))
                .thenReturn(200L, 201L, 202L);
        // mock 用户 300L
        when(userService.createUser(argThat(user -> {
            assertEquals("yunai", user.getUsername());
            assertEquals("yuanma", user.getPassword());
            assertEquals("芋道", user.getNickname());
            assertEquals("15601691300", user.getMobile());
            return true;
        }))).thenReturn(300L);

        // 准备参数
        TenantSaveReqVO reqVO = randomPojo(TenantSaveReqVO.class, o -> {
            o.setContactName("芋道");
            o.setContactMobile("15601691300");
            o.setPackageId(100L);
            o.setStatus(randomCommonStatus());
            o.setWebsites(singletonList("https://www.iocoder.cn"));
            o.setUsername("yunai");
            o.setPassword("yuanma");
        }).setId(null); // 设置为 null，方便后面校验

        // 调用
        Long tenantId = tenantService.createTenant(reqVO);
        // 断言
        assertNotNull(tenantId);
        // 校验记录的属性是否正确
        TenantDO tenant = tenantMapper.selectById(tenantId);
        assertPojoEquals(reqVO, tenant, "id");
        assertEquals(300L, tenant.getContactUserId());
        verify(roleService).createRole(argThat(role ->
                RoleCodeEnum.TENANT_ADMIN.getCode().equals(role.getCode())
                        && "机构管理员".equals(role.getName()) && role.getSort() == 0),
                eq(RoleTypeEnum.SYSTEM.getType()));
        verify(roleService).createRole(argThat(role ->
                RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode().equals(role.getCode())
                        && "康复治疗师".equals(role.getName())), eq(RoleTypeEnum.SYSTEM.getType()));
        verify(roleService).createRole(argThat(role ->
                RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode().equals(role.getCode())
                        && "临床审核人".equals(role.getName())), eq(RoleTypeEnum.SYSTEM.getType()));
        verify(roleService).updateTenantTemplateRoleDataScope(eq(200L),
                eq(TenantRolePermissionTemplate.definition(RoleCodeEnum.TENANT_ADMIN.getCode()).getDataScope()));
        verify(roleService).updateTenantTemplateRoleDataScope(eq(201L),
                eq(TenantRolePermissionTemplate.definition(RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()).getDataScope()));
        verify(roleService).updateTenantTemplateRoleDataScope(eq(202L),
                eq(TenantRolePermissionTemplate.definition(RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode()).getDataScope()));
        verify(permissionService).assignRoleMenu(eq(200L), eq(TenantRolePermissionTemplate.resolveMenuIds(
                RoleCodeEnum.TENANT_ADMIN.getCode(), packageMenuIds, menus)));
        verify(permissionService).assignRoleMenu(eq(201L), eq(TenantRolePermissionTemplate.resolveMenuIds(
                RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode(), packageMenuIds, menus)));
        verify(permissionService).assignRoleMenu(eq(202L), eq(TenantRolePermissionTemplate.resolveMenuIds(
                RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode(), packageMenuIds, menus)));
        // Only the institution administrator role is assigned to the bootstrap user.
        verify(permissionService).assignUserRole(eq(300L), eq(singleton(200L)));
    }

    @Test
    public void testUpdateTenant_success() {
        // mock 数据
        TenantDO dbTenant = randomPojo(TenantDO.class, o -> o.setStatus(randomCommonStatus()).setPackageId(1L));
        tenantMapper.insert(dbTenant);// @Sql: 先插入出一条存在的数据
        // 准备参数
        TenantSaveReqVO reqVO = randomPojo(TenantSaveReqVO.class, o -> {
            o.setId(dbTenant.getId()); // 设置更新的 ID
            o.setPackageId(2L);
            o.setStatus(randomCommonStatus());
            o.setWebsites(singletonList(randomString()));
        });

        // mock 套餐
        List<MenuDO> menus = roleTemplateMenus();
        Set<Long> packageMenuIds = menus.stream().map(MenuDO::getId).collect(Collectors.toSet());
        packageMenuIds.add(200L);
        packageMenuIds.add(201L);
        TenantPackageDO tenantPackage = randomPojo(TenantPackageDO.class,
                o -> o.setMenuIds(packageMenuIds));
        when(tenantPackageService.validTenantPackage(eq(reqVO.getPackageId()))).thenReturn(tenantPackage);
        when(menuService.getMenuList()).thenReturn(menus);
        // mock 所有角色
        RoleDO role100 = randomPojo(RoleDO.class, o -> o.setId(100L).setCode(RoleCodeEnum.TENANT_ADMIN.getCode())
                .setType(RoleTypeEnum.SYSTEM.getType()).setStatus(CommonStatusEnum.ENABLE.getStatus()));
        role100.setTenantId(dbTenant.getId());
        RoleDO role102 = randomPojo(RoleDO.class, o -> o.setId(102L).setCode(RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode())
                .setType(RoleTypeEnum.SYSTEM.getType()).setStatus(CommonStatusEnum.ENABLE.getStatus()));
        role102.setTenantId(dbTenant.getId());
        RoleDO role103 = randomPojo(RoleDO.class, o -> o.setId(103L).setCode(RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode())
                .setType(RoleTypeEnum.SYSTEM.getType()).setStatus(CommonStatusEnum.ENABLE.getStatus()));
        role103.setTenantId(dbTenant.getId());
        // Legacy rehab_therapist remains a custom role and must not be mistaken
        // for the new tenant_rehab_therapist SYSTEM template.
        RoleDO role101 = randomPojo(RoleDO.class, o -> o.setId(101L)
                .setCode(RoleCodeEnum.REHAB_THERAPIST.getCode()).setType(RoleTypeEnum.CUSTOM.getType()));
        role101.setTenantId(dbTenant.getId());
        when(roleService.getRoleList()).thenReturn(asList(role100, role102, role103, role101));
        // mock 每个角色的权限
        when(permissionService.getRoleMenuListByRoleId(eq(101L))).thenReturn(asSet(201L, 202L));

        // 调用
        tenantService.updateTenant(reqVO);
        // 校验是否更新正确
        TenantDO tenant = tenantMapper.selectById(reqVO.getId()); // 获取最新的
        assertPojoEquals(reqVO, tenant);
        // verify 设置角色权限
        verify(permissionService).assignRoleMenu(eq(100L), eq(TenantRolePermissionTemplate.resolveMenuIds(
                RoleCodeEnum.TENANT_ADMIN.getCode(), packageMenuIds, menus)));
        verify(permissionService).assignRoleMenu(eq(102L), eq(TenantRolePermissionTemplate.resolveMenuIds(
                RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode(), packageMenuIds, menus)));
        verify(permissionService).assignRoleMenu(eq(103L), eq(TenantRolePermissionTemplate.resolveMenuIds(
                RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode(), packageMenuIds, menus)));
        verify(permissionService).assignRoleMenu(eq(101L), eq(asSet(201L)));
        verify(roleService).updateTenantTemplateRoleDataScope(eq(100L),
                eq(TenantRolePermissionTemplate.definition(RoleCodeEnum.TENANT_ADMIN.getCode()).getDataScope()));
        verify(roleService).updateTenantTemplateRoleDataScope(eq(102L),
                eq(TenantRolePermissionTemplate.definition(RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()).getDataScope()));
        verify(roleService).updateTenantTemplateRoleDataScope(eq(103L),
                eq(TenantRolePermissionTemplate.definition(RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode()).getDataScope()));
    }

    @Test
    public void testUpdateTenantRoleMenuCreatesMissingStaffTemplatesWithoutAssigningUsers() {
        List<MenuDO> menus = roleTemplateMenus();
        Set<Long> packageMenuIds = menus.stream().map(MenuDO::getId).collect(Collectors.toSet());
        when(menuService.getMenuList()).thenReturn(menus);
        RoleDO adminRole = randomPojo(RoleDO.class, o -> o.setId(500L)
                .setCode(RoleCodeEnum.TENANT_ADMIN.getCode()).setType(RoleTypeEnum.SYSTEM.getType())
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        adminRole.setTenantId(100L);
        when(roleService.getRoleList()).thenReturn(singletonList(adminRole));
        when(roleService.createRole(any(RoleSaveReqVO.class), eq(RoleTypeEnum.SYSTEM.getType())))
                .thenReturn(501L, 502L);

        tenantService.updateTenantRoleMenu(100L, packageMenuIds);

        verify(roleService).createRole(argThat(role ->
                RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode().equals(role.getCode())), eq(RoleTypeEnum.SYSTEM.getType()));
        verify(roleService).createRole(argThat(role ->
                RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode().equals(role.getCode())), eq(RoleTypeEnum.SYSTEM.getType()));
        verify(permissionService).assignRoleMenu(eq(500L), eq(TenantRolePermissionTemplate.resolveMenuIds(
                RoleCodeEnum.TENANT_ADMIN.getCode(), packageMenuIds, menus)));
        verify(permissionService, times(2)).assignRoleMenu(eq(501L), eq(TenantRolePermissionTemplate.resolveMenuIds(
                RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode(), packageMenuIds, menus)));
        verify(permissionService, times(2)).assignRoleMenu(eq(502L), eq(TenantRolePermissionTemplate.resolveMenuIds(
                RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode(), packageMenuIds, menus)));
        verify(permissionService, never()).assignUserRole(anyLong(), anySet());
    }

    @Test
    public void testUpdateTenant_notExists() {
        // 准备参数
        TenantSaveReqVO reqVO = randomPojo(TenantSaveReqVO.class);

        // 调用, 并断言异常
        assertServiceException(() -> tenantService.updateTenant(reqVO), TENANT_NOT_EXISTS);
    }

    @Test
    public void testUpdateTenant_system() {
        // mock 数据
        TenantDO dbTenant = randomPojo(TenantDO.class, o -> o.setPackageId(PACKAGE_ID_SYSTEM));
        tenantMapper.insert(dbTenant);// @Sql: 先插入出一条存在的数据
        // 准备参数
        TenantSaveReqVO reqVO = randomPojo(TenantSaveReqVO.class, o -> {
            o.setId(dbTenant.getId()); // 设置更新的 ID
        });

        // 调用，校验业务异常
        assertServiceException(() -> tenantService.updateTenant(reqVO), TENANT_CAN_NOT_UPDATE_SYSTEM);
    }

    @Test
    public void testDeleteTenant_success() {
        // mock 数据
        TenantDO dbTenant = randomPojo(TenantDO.class,
                o -> o.setStatus(randomCommonStatus()));
        tenantMapper.insert(dbTenant);// @Sql: 先插入出一条存在的数据
        // 准备参数
        Long id = dbTenant.getId();

        // 调用
        tenantService.deleteTenant(id);
        // 校验数据不存在了
        assertNull(tenantMapper.selectById(id));
    }

    @Test
    public void testDeleteTenant_notExists() {
        // 准备参数
        Long id = randomLongId();

        // 调用, 并断言异常
        assertServiceException(() -> tenantService.deleteTenant(id), TENANT_NOT_EXISTS);
    }

    @Test
    public void testDeleteTenant_system() {
        // mock 数据
        TenantDO dbTenant = randomPojo(TenantDO.class, o -> o.setPackageId(PACKAGE_ID_SYSTEM));
        tenantMapper.insert(dbTenant);// @Sql: 先插入出一条存在的数据
        // 准备参数
        Long id = dbTenant.getId();

        // 调用, 并断言异常
        assertServiceException(() -> tenantService.deleteTenant(id), TENANT_CAN_NOT_UPDATE_SYSTEM);
    }

    @Test
    public void testGetTenant() {
        // mock 数据
        TenantDO dbTenant = randomPojo(TenantDO.class);
        tenantMapper.insert(dbTenant);// @Sql: 先插入出一条存在的数据
        // 准备参数
        Long id = dbTenant.getId();

        // 调用
        TenantDO result = tenantService.getTenant(id);
        // 校验存在
        assertPojoEquals(result, dbTenant);
    }

    @Test
    public void testGetTenantPage() {
        // mock 数据
        TenantDO dbTenant = randomPojo(TenantDO.class, o -> { // 等会查询到
            o.setName("芋道源码");
            o.setContactName("芋艿");
            o.setContactMobile("15601691300");
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setCreateTime(buildTime(2020, 12, 12));
        });
        tenantMapper.insert(dbTenant);
        // 测试 name 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setName(randomString())));
        // 测试 contactName 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setContactName(randomString())));
        // 测试 contactMobile 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setContactMobile(randomString())));
        // 测试 status 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setStatus(CommonStatusEnum.DISABLE.getStatus())));
        // 测试 createTime 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setCreateTime(buildTime(2021, 12, 12))));
        // 准备参数
        TenantPageReqVO reqVO = new TenantPageReqVO();
        reqVO.setName("芋道");
        reqVO.setContactName("艿");
        reqVO.setContactMobile("1560");
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setCreateTime(buildBetweenTime(2020, 12, 1, 2020, 12, 24));

        // 调用
        PageResult<TenantDO> pageResult = tenantService.getTenantPage(reqVO);
        // 断言
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertPojoEquals(dbTenant, pageResult.getList().get(0));
    }

    @Test
    public void testGetTenantByName() {
        // mock 数据
        TenantDO dbTenant = randomPojo(TenantDO.class, o -> o.setName("芋道"));
        tenantMapper.insert(dbTenant);// @Sql: 先插入出一条存在的数据

        // 调用
        TenantDO result = tenantService.getTenantByName("芋道");
        // 校验存在
        assertPojoEquals(result, dbTenant);
    }

    @Test
    @Disabled // H2 不支持 find_in_set 函数
    public void testGetTenantByWebsite() {
        // mock 数据
        TenantDO dbTenant = randomPojo(TenantDO.class, o -> o.setWebsites(singletonList("https://www.iocoder.cn")));
        tenantMapper.insert(dbTenant);// @Sql: 先插入出一条存在的数据

        // 调用
        TenantDO result = tenantService.getTenantByWebsite("https://www.iocoder.cn");
        // 校验存在
        assertPojoEquals(result, dbTenant);
    }

    @Test
    public void testGetTenantListByPackageId() {
        // mock 数据
        TenantDO dbTenant1 = randomPojo(TenantDO.class, o -> o.setPackageId(1L));
        tenantMapper.insert(dbTenant1);// @Sql: 先插入出一条存在的数据
        TenantDO dbTenant2 = randomPojo(TenantDO.class, o -> o.setPackageId(2L));
        tenantMapper.insert(dbTenant2);// @Sql: 先插入出一条存在的数据

        // 调用
        List<TenantDO> result = tenantService.getTenantListByPackageId(1L);
        assertEquals(1, result.size());
        assertPojoEquals(dbTenant1, result.get(0));
    }

    @Test
    public void testGetTenantCountByPackageId() {
        // mock 数据
        TenantDO dbTenant1 = randomPojo(TenantDO.class, o -> o.setPackageId(1L));
        tenantMapper.insert(dbTenant1);// @Sql: 先插入出一条存在的数据
        TenantDO dbTenant2 = randomPojo(TenantDO.class, o -> o.setPackageId(2L));
        tenantMapper.insert(dbTenant2);// @Sql: 先插入出一条存在的数据

        // 调用
        Long count = tenantService.getTenantCountByPackageId(1L);
        assertEquals(1, count);
    }

    @Test
    public void testHandleTenantInfo_disable() {
        // 准备参数
        TenantInfoHandler handler = mock(TenantInfoHandler.class);
        // mock 禁用
        when(tenantProperties.getEnable()).thenReturn(false);

        // 调用
        tenantService.handleTenantInfo(handler);
        // 断言
        verify(handler, never()).handle(any());
    }

    @Test
    public void testHandleTenantInfo_success() {
        // 准备参数
        TenantInfoHandler handler = mock(TenantInfoHandler.class);
        // mock 未禁用
        when(tenantProperties.getEnable()).thenReturn(true);
        // mock 租户
        TenantDO dbTenant = randomPojo(TenantDO.class);
        tenantMapper.insert(dbTenant);// @Sql: 先插入出一条存在的数据
        TenantContextHolder.setTenantId(dbTenant.getId());

        // 调用
        tenantService.handleTenantInfo(handler);
        // 断言
        verify(handler).handle(argThat(argument -> {
            assertPojoEquals(dbTenant, argument);
            return true;
        }));
    }

    @Test
    public void testHandleTenantMenu_disable() {
        // 准备参数
        TenantMenuHandler handler = mock(TenantMenuHandler.class);
        // mock 禁用
        when(tenantProperties.getEnable()).thenReturn(false);

        // 调用
        tenantService.handleTenantMenu(handler);
        // 断言
        verify(handler, never()).handle(any());
    }

    @Test // 系统租户的情况
    public void testHandleTenantMenu_system() {
        // 准备参数
        TenantMenuHandler handler = mock(TenantMenuHandler.class);
        // mock 未禁用
        when(tenantProperties.getEnable()).thenReturn(true);
        // mock 租户
        TenantDO dbTenant = randomPojo(TenantDO.class, o -> o.setPackageId(PACKAGE_ID_SYSTEM));
        tenantMapper.insert(dbTenant);// @Sql: 先插入出一条存在的数据
        TenantContextHolder.setTenantId(dbTenant.getId());
        // mock 菜单
        when(menuService.getMenuList()).thenReturn(Arrays.asList(randomPojo(MenuDO.class, o -> o.setId(100L)),
                randomPojo(MenuDO.class, o -> o.setId(101L))));

        // 调用
        tenantService.handleTenantMenu(handler);
        // 断言
        verify(handler).handle(asSet(100L, 101L));
    }

    @Test // 普通租户的情况
    public void testHandleTenantMenu_normal() {
        // 准备参数
        TenantMenuHandler handler = mock(TenantMenuHandler.class);
        // mock 未禁用
        when(tenantProperties.getEnable()).thenReturn(true);
        // mock 租户
        TenantDO dbTenant = randomPojo(TenantDO.class, o -> o.setPackageId(200L));
        tenantMapper.insert(dbTenant);// @Sql: 先插入出一条存在的数据
        TenantContextHolder.setTenantId(dbTenant.getId());
        // mock 菜单
        when(tenantPackageService.getTenantPackage(eq(200L))).thenReturn(randomPojo(TenantPackageDO.class,
                o -> o.setMenuIds(asSet(100L, 101L))));

        // 调用
        tenantService.handleTenantMenu(handler);
        // 断言
        verify(handler).handle(asSet(100L, 101L));
    }

    private static List<MenuDO> roleTemplateMenus() {
        List<MenuDO> menus = new java.util.ArrayList<>();
        addMenu(menus, 1L, 0L, 1, null);
        addMenu(menus, 9000L, 0L, 1, null);
        addMenu(menus, 100L, 1L, 2, "system:user:list");
        addMenu(menus, 1001L, 100L, 3, "system:user:query");
        addMenu(menus, 1002L, 100L, 3, "system:user:create");
        addMenu(menus, 1003L, 100L, 3, "system:user:update");
        addMenu(menus, 9001L, 9000L, 2, "rehab:dashboard:view");
        addMenu(menus, 9002L, 9000L, 2, "rehab:patient:view");
        addMenu(menus, 9003L, 9000L, 2, "rehab:assessment:view");
        addMenu(menus, 9004L, 9000L, 2, "rehab:report:view");
        addMenu(menus, 9101L, 9002L, 3, "rehab:patient:create");
        addMenu(menus, 9102L, 9002L, 3, "rehab:patient:update");
        addMenu(menus, 9104L, 9002L, 3, "rehab:patient:detail");
        addMenu(menus, 9201L, 9003L, 3, "rehab:assessment:create");
        addMenu(menus, 9202L, 9003L, 3, "rehab:assessment:update");
        addMenu(menus, 9203L, 9003L, 3, "rehab:assessment:detail");
        addMenu(menus, 9302L, 9004L, 3, "rehab:report:detail");
        addMenu(menus, 9303L, 9004L, 3, "rehab:report:preview");
        addMenu(menus, 9304L, 9004L, 3, "rehab:report:review");
        addMenu(menus, 9305L, 9004L, 3, "rehab:report:approve");
        addMenu(menus, 9306L, 9004L, 3, "rehab:report:lock");
        addMenu(menus, 9308L, 9004L, 3, "rehab:report:version:view");
        addMenu(menus, 9700L, 9000L, 2, "rehab:motion:query");
        addMenu(menus, 9710L, 9700L, 3, "rehab:motion:query");
        addMenu(menus, 9711L, 9700L, 3, "rehab:motion:create");
        addMenu(menus, 9712L, 9700L, 3, "rehab:motion:upload");
        addMenu(menus, 9713L, 9700L, 3, "rehab:motion:process");
        addMenu(menus, 9714L, 9700L, 3, "rehab:motion:review");
        addMenu(menus, 9715L, 9700L, 3, "rehab:motion:sign");
        addMenu(menus, 9717L, 9700L, 3, "rehab:motion:audit");
        return menus;
    }

    private static void addMenu(List<MenuDO> menus, Long id, Long parentId, Integer type, String permission) {
        MenuDO menu = new MenuDO();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setType(type);
        menu.setPermission(permission);
        menu.setStatus(CommonStatusEnum.ENABLE.getStatus());
        menus.add(menu);
    }
}
