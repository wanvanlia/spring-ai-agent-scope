package com.antifraud.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 关停工单表 (t_shutdown_order)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_shutdown_order")
public class ShutdownOrder extends Model<ShutdownOrder> {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long caseId;

    private String phone;

    private String phoneMasked;

    private String reason;

    /** 运营商接口返回(JSON) */
    private String carrierApiResp;

    /** SUCCESS/FAIL/SKIP */
    private String notifyStatus;

    /** PENDING/SUBMITTED/SUCCESS/FAILED/CANCELLED */
    private String status;

    private Long operatorId;

    /** 0-人工 1-自动 */
    private Integer isAuto;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
