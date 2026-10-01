package com.fc.v2.haz;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.service.impl.TIsHazCardServiceImpl;
import com.fc.v2.service.impl.TIsLimRuleServiceImpl;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.Date;

/**
 * 辨识定级测试专用的最小 Spring 上下文：H2（MySQL 方言）+ MyBatis-Plus + 两个 service，
 * 不起 shiro/quartz/redis/druid。每个测试方法事务回滚，互不串数。
 *
 * @author fuce
 * @date 2026-10-01
 */
@Configuration
@MapperScan(basePackages = "com.fc.v2.mapper.auto")
public class HazITConfig {

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        // MODE=MySQL：吃反引号/分页方言；DB_CLOSE_DELAY=-1：内存库上下文期间常驻。
        ds.setUrl("jdbc:h2:mem:haztest;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    @Bean
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("haz/schema-haz.sql"));
        populator.addScript(new ClassPathResource("haz/seed-haz.sql"));
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    @Bean
    public GlobalConfig globalConfig() {
        return new GlobalConfig().setMetaObjectHandler(new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createBy", String.class, "tester");
                this.strictInsertFill(metaObject, "createTime", Date.class, new Date());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateBy", String.class, "tester");
                this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
            }
        });
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, GlobalConfig globalConfig)
            throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setGlobalConfig(globalConfig);
        return factoryBean.getObject();
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public TIsLimRuleServiceImpl limRuleService() {
        return new TIsLimRuleServiceImpl();
    }

    @Bean
    public TIsHazCardServiceImpl hazCardService() {
        return new TIsHazCardServiceImpl();
    }
}
