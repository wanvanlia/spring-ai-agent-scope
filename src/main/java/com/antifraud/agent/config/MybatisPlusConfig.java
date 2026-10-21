package com.antifraud.agent.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 * <p>分页拦截器必须显式注册, 否则 {@code selectPage} 不会拼接 limit, 会一次性返回全表数据。
 * <p>注意: 3.5.9+ 起 {@code PaginationInnerInterceptor} 位于独立模块
 * {@code com.baomidou:mybatis-plus-jsqlparser-4.9}(starter 中为 optional, 需显式引入),
 * 已在 pom.xml 中声明该依赖。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
