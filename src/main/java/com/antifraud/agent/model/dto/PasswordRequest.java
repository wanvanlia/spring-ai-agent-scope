package com.antifraud.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 密码相关请求。
 * <ul>
 *   <li>本人修改密码: oldPassword + newPassword</li>
 *   <li>管理员重置他人密码: 仅 newPassword(oldPassword 可为空)</li>
 * </ul>
 */
@Data
public class PasswordRequest {

    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度须为 6-64 位")
    private String newPassword;
}
