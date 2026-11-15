package cn.iocoder.yudao.module.rehab.service;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.ResolvableType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mockito;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
 * Synthetic ORM/service integration fixture, NOT MySQL migration or browser proof.
 * Builds isolated H2 tables from the real mapped fields; all CRUD uses real MP
 * mappers and transaction proxies. Only external side-effect ports are mocks.
 */
public final class ClinicalFlowDatabase implements AutoCloseable {
    public final JdbcTemplate jdbc;
    private final DriverManagerDataSource dataSource;
    private final DataSourceTransactionManager manager;
    private final MybatisConfiguration configuration = new MybatisConfiguration();
    private final Map<Class<?>, Object> mappers = new HashMap<>();
    private final Set<Class<?>> entities = new HashSet<>();
    private SqlSessionTemplate session;
    public ClinicalFlowDatabase() {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:clinical_flow_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(dataSource); manager = new DataSourceTransactionManager(dataSource);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        configuration.setCacheEnabled(false);
        TenantContextHolder.setTenantId(71L);
    }
    public void registerMappers(Object... targets) throws Exception {
        Set<Class<?>> types = new HashSet<>();
        for (Object target : targets) for (Field field : target.getClass().getDeclaredFields()) {
            if (!BaseMapper.class.isAssignableFrom(field.getType())) continue;
            Class<?> mapper = field.getType(); types.add(mapper);
            Class<?> entity = ResolvableType.forClass(mapper).as(BaseMapper.class).getGeneric(0).resolve();
            if (entity == null) throw new IllegalStateException("synthetic mapper entity missing");
            createTable(entity);
        }
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public Expression getTenantId() { return new LongValue(TenantContextHolder.getRequiredTenantId()); }
        }));
        configuration.addInterceptor(interceptor);
        for (Class<?> type : types) configuration.addMapper(type);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource); factory.setConfiguration(configuration);
        factory.setGlobalConfig(new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO)));
        session = new SqlSessionTemplate(factory.getObject());
        for (Class<?> type : types) mappers.put(type, session.getMapper(type));
    }
    private void createTable(Class<?> entity) {
        if (!entities.add(entity)) return;
        String table = entity.getAnnotation(TableName.class).value();
        Map<String,String> columns = new LinkedHashMap<>();
        for (Class<?> c = entity; c != Object.class; c = c.getSuperclass()) for (Field field : c.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            TableField mapped = field.getAnnotation(TableField.class);
            if (mapped != null && !mapped.exist()) continue;
            String name = mapped != null && !mapped.value().isEmpty() ? mapped.value() : field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z_]+")) throw new IllegalStateException("review synthetic mapped identifier");
            Class<?> type = field.getType();
            String sql = "VARCHAR(1000000)";
            if (type == Long.class || type == long.class) sql = "BIGINT";
            if (type == Integer.class || type == int.class) sql = "INTEGER";
            if (type == BigDecimal.class) sql = "DECIMAL(38,12)";
            if (type == Boolean.class || type == boolean.class) sql = "BOOLEAN DEFAULT FALSE";
            if (type == LocalDate.class) sql = "DATE";
            if (type == LocalDateTime.class) sql = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
            if (field.getAnnotation(TableId.class) != null) sql = "BIGINT AUTO_INCREMENT PRIMARY KEY";
            columns.put(name, name + " " + sql);
        }
        columns.put("tenant_id", "tenant_id BIGINT DEFAULT 71 NOT NULL");
        jdbc.execute("CREATE TABLE " + table + "(" + String.join(",", columns.values()) + ")");
    }
    public void wire(Object target, Object... ports) {
        for (Field field : target.getClass().getDeclaredFields()) {
            if (field.getAnnotation(javax.annotation.Resource.class) == null) continue;
            Object value = mappers.get(field.getType());
            for (Object port : ports) if (field.getType().isInstance(port)) { value = port; break; }
            if (field.getType() == JdbcTemplate.class) value = jdbc;
            if (value == null && field.getType().isInterface()) value = Mockito.mock(field.getType());
            if (value != null) ReflectionTestUtils.setField(target, field.getName(), value);
        }
    }
    @SuppressWarnings("unchecked") public <T> T proxy(T target) {
        ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }
    public <T> T mapper(Class<T> type) { return type.cast(mappers.get(type)); }
    @Override public void close() {
        TenantContextHolder.clear();
        // SHUTDOWN closes the H2 connection itself; do not ask JdbcTemplate to
        // reset it afterward. This closes only this fixture's unique memory DB.
        try (java.sql.Connection connection = dataSource.getConnection(); java.sql.Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (java.sql.SQLException error) { throw new IllegalStateException("synthetic DB cleanup failed", error); }
    }
}
