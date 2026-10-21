package com.antifraud.agent.model.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改用户信息请求(不含密码, 改密码走专用接口)。
 */
@Data
public class UserUpdateRequest {

    @Size(max = 64, message = "姓名长度不能超过 64")
    private String realName;

    @Pattern(regexp = "USER|AUDITOR|ADMIN", message = "角色只能为 USER / AUDITOR / ADMIN")
    private String role;

    @Size(max = 32, message = "手机号长度不能超过 32")
    private String phone;

    /** 0-禁用 1-启用 */
    private Integer status;
}
