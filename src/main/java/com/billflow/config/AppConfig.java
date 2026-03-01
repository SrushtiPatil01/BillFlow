package com.billflow.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Core application configuration. Manually configures the DataSource (HikariCP),
 * Hibernate SessionFactory, and transaction manager — the three things Spring Boot
 * normally auto-configures.
 */
@Configuration
@ComponentScan(basePackages = "com.billflow")
@EnableTransactionManagement
@EnableScheduling
public class AppConfig {

    // -------------------------------------------------------
    // DataSource — HikariCP connection pool
    // -------------------------------------------------------
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env("DB_URL", "jdbc:mysql://localhost:3306/billflow"));
        config.setUsername(env("DB_USER", "billflow"));
        config.setPassword(env("DB_PASS", "billflow_secret"));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Pool sizing: keep small to avoid exhausting DB connections.
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300_000);       // 5 minutes
        config.setConnectionTimeout(20_000);  // 20 seconds
        config.setMaxLifetime(1_200_000);     // 20 minutes

        return new HikariDataSource(config);
    }

    // -------------------------------------------------------
    // Hibernate SessionFactory
    // -------------------------------------------------------
    @Bean
    public LocalSessionFactoryBean sessionFactory(DataSource dataSource) {
        LocalSessionFactoryBean factory = new LocalSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.billflow.model");

        Properties props = new Properties();
        props.put(AvailableSettings.DIALECT, "org.hibernate.dialect.MySQLDialect");
        props.put(AvailableSettings.SHOW_SQL, "true");
        props.put(AvailableSettings.FORMAT_SQL, "true");
        props.put(AvailableSettings.HBM2DDL_AUTO, "validate");
        factory.setHibernateProperties(props);

        return factory;
    }

    // -------------------------------------------------------
    // Transaction manager — wires SessionFactory with @Transactional
    // -------------------------------------------------------
    @Bean
    public PlatformTransactionManager transactionManager(SessionFactory sessionFactory) {
        HibernateTransactionManager txManager = new HibernateTransactionManager();
        txManager.setSessionFactory(sessionFactory);
        return txManager;
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }
}