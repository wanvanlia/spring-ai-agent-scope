package com.antifraud.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增用户请求。
 */
@Data
public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64")
    private String username;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度须为 6-64 位")
    private String password;

    @Size(max = 64, message = "姓名长度不能超过 64")
    private String realName;

    /** USER / AUDITOR / ADMIN */
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "USER|AUDITOR|ADMIN", message = "角色只能为 USER / AUDITOR / ADMIN")
    private String role;

    @Size(max = 32, message = "手机号长度不能超过 32")
    private String phone;

    /** 0-禁用 1-启用 */
    private Integer status;
}
