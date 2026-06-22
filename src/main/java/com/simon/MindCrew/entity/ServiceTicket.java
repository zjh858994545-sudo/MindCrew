package com.simon.MindCrew.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("service_ticket")
public class ServiceTicket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketNo;
    private String title;
    private String requester;
    private String requesterRole;
    private String department;
    private String priority;
    private String channel;
    private String status;
    private String category;
    private String question;
    private String expectedOutcome;
    private String kbScope;
    private BigDecimal confidence;
    private String answerDraft;
    private String finalAnswer;
    private String sourceSummary;
    private String aiTraceId;
    private Integer accepted;
    private String feedbackStatus;
    private Long goldenPairId;
    private String resolutionOwner;
    private LocalDateTime resolvedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
