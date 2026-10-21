package com.antifraud.agent.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PII 脱敏工具: 手机号/姓名在入库与日志前掩码处理。
 */
@Component
public class MaskingUtil {

    @Value("${antifraud.masking.phone-keep-head:3}")
    private int phoneKeepHead;

    @Value("${antifraud.masking.phone-keep-tail:4}")
    private int phoneKeepTail;

    /** 手机号脱敏: 138****1234 */
    public String maskPhone(String phone) {
        if (phone == null || phone.length() < phoneKeepHead + phoneKeepTail) {
            return phone;
        }
        int len = phone.length();
        StringBuilder sb = new StringBuilder();
        sb.append(phone, 0, phoneKeepHead);
        sb.append("*".repeat(len - phoneKeepHead - phoneKeepTail));
        sb.append(phone, len - phoneKeepTail, len);
        return sb.toString();
    }

    /** 姓名脱敏: 张** */
    public String maskName(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}
