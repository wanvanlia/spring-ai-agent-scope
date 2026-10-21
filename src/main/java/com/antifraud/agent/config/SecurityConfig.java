package com.antifraud.agent.config;

import com.antifraud.agent.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置: 无状态 JWT + 基于角色的访问控制。
 * <p>权限分级: USER(仅查询/chat) / AUDITOR(审核) / ADMIN(关停+知识库)。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/antifraud/auth/**", "/antifraud/actuator/**",
                                "/auth/**", "/actuator/**").permitAll()
                        // 前端静态资源(登录页/工作台)允许匿名访问, 数据接口仍需 JWT 鉴权
                        .requestMatchers("/antifraud/", "/antifraud/index.html",
                                "/antifraud/css/*", "/antifraud/js/*", "/antifraud/partials/*",
                                "/", "/index.html", "/css/*", "/js/*", "/partials/*").permitAll()
                        .requestMatchers("/antifraud/api/ingest", "/antifraud/api/ingest/batch").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
