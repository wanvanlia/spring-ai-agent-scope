package com.antifraud.agent.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 数据源配置:
 * - MySQL(@Primary): 业务库 t_* , MyBatis-Plus 默认使用。
 * - 向量库改由 Milvus 承载(Spring AI MilvusVectorStore 自动装配, gRPC 连接, 非 JDBC)。
 * <p>由于使用了自定义命名空间(spring.datasource.mysql), Spring Boot 默认
 * DataSourceAutoConfiguration 不会自动创建数据源, 必须显式声明。
 * <p>注意: 不能直接把 spring.datasource.mysql.* 绑定到 HikariDataSource——
 * Hikari 的连接地址属性名为 jdbcUrl, 而配置中写的是 url, 会被当作未知属性忽略,
 * 导致 "jdbcUrl is required with driverClassName"。因此这里先用
 * {@link DataSourceProperties} 承接(url/username/password/driver-class-name),
 * 再由它初始化 HikariDataSource(内部会把 url 转换为 jdbcUrl)。
 */
@Configuration
public class DataSourceConfig {

    /** spring.datasource.mysql 下的连接参数: url / username / password / driver-class-name */
    @Bean
    @ConfigurationProperties("spring.datasource.mysql")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    /** spring.datasource.mysql.hikari 下的连接池参数: pool-name / maximum-pool-size 等 */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.mysql.hikari")
    public HikariDataSource mysqlDataSource(DataSourceProperties mysqlDataSourceProperties) {
        return mysqlDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    public JdbcTemplate mysqlJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
