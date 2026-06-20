package com.simon.MindCrew.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("safety_event_log")
public class SafetyEventLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private String userId;
    private String riskType;
    private String riskLevel;
    private String action;
    private String matchedRule;
    private String inputSummary;
    private LocalDateTime createTime;
}
