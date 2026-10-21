package com.antifraud.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 号码信息表 (t_phone_number)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_phone_number")
public class PhoneNumber extends Model<PhoneNumber> {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;

    private String phoneMasked;

    /** CMCC/CUCC/CTCC */
    private String carrier;

    private String region;

    @TableField("call_count_24h")
    private Integer callCount24h;

    private Integer avgDurationSec;

    private Integer markedCount;

    /** 0-否 1-是 */
    private Integer blacklistFlag;

    /** LOW/MEDIUM/HIGH */
    private String riskLevel;

    /** ACTIVE/SHUTDOWN */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
