package com.antifraud.agent.controller;

import com.antifraud.agent.common.Result;
import com.antifraud.agent.entity.User;
import com.antifraud.agent.mapper.UserMapper;
import com.antifraud.agent.model.dto.PageResult;
import com.antifraud.agent.model.dto.PasswordRequest;
import com.antifraud.agent.model.dto.UserCreateRequest;
import com.antifraud.agent.model.dto.UserUpdateRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 用户管理接口: 查询 / 新增 / 修改 / 重置密码(管理员) 与 本人修改密码。
 * <p>所有返回的用户对象都会清除 password 字段, 避免密码哈希外泄。
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final long MAX_SIZE = 100L;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /** 用户列表(分页) */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<User>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<User> cond = new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(username), User::getUsername, username)
                .eq(StringUtils.hasText(role), User::getRole, role)
                .eq(status != null, User::getStatus, status)
                .orderByDesc(User::getId);

        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_SIZE);
        Page<User> p = userMapper.selectPage(new Page<>(current, pageSize), cond);
        if (p != null && p.getRecords() != null) {
            p.getRecords().forEach(u -> u.setPassword(null));
        }
        return Result.success(PageResult.of(p == null ? null : p.getRecords(),
                p == null ? 0 : p.getTotal(), current, pageSize));
    }

    /** 新增用户 */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Long> create(@Valid @RequestBody UserCreateRequest req) {
        Long existed = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (existed != null && existed > 0) {
            throw new IllegalArgumentException("用户名已存在: " + req.getUsername());
        }
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setRealName(req.getRealName());
        u.setRole(req.getRole());
        u.setPhone(req.getPhone());
        u.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        LocalDateTime now = LocalDateTime.now();
        u.setCreateTime(now);
        u.setUpdateTime(now);
        userMapper.insert(u);
        log.info("新增用户 {} role={} operatorCreated", u.getUsername(), u.getRole());
        return Result.success(u.getId());
    }

    /** 修改用户基本信息(不含密码) */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest req) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new IllegalArgumentException("用户不存在: " + id);
        }
        if (req.getRealName() != null) {
            u.setRealName(req.getRealName());
        }
        if (StringUtils.hasText(req.getRole())) {
            u.setRole(req.getRole());
        }
        if (req.getPhone() != null) {
            u.setPhone(req.getPhone());
        }
        if (req.getStatus() != null) {
            u.setStatus(req.getStatus());
        }
        u.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(u);
        return Result.success();
    }

    /** 管理员重置指定用户密码(无需原密码) */
    @PostMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordRequest req) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new IllegalArgumentException("用户不存在: " + id);
        }
        u.setPassword(passwordEncoder.encode(req.getNewPassword()));
        u.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(u);
        log.info("管理员重置用户密码 id={} username={}", id, u.getUsername());
        return Result.success();
    }

    /** 本人修改密码(需校验原密码) */
    @PostMapping("/me/password")
    public Result<Void> changeMyPassword(@Valid @RequestBody PasswordRequest req, Authentication auth) {
        String username = auth == null ? null : auth.getName();
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("无法识别当前登录用户");
        }
        User u = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (u == null) {
            throw new IllegalArgumentException("用户不存在: " + username);
        }
        if (!StringUtils.hasText(req.getOldPassword())
                || !passwordEncoder.matches(req.getOldPassword(), u.getPassword())) {
            throw new IllegalArgumentException("原密码不正确");
        }
        u.setPassword(passwordEncoder.encode(req.getNewPassword()));
        u.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(u);
        return Result.success();
    }
}
