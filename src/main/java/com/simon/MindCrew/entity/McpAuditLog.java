package com.simon.MindCrew.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mcp_audit_log")
public class McpAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private String clientId;
    private String userId;
    private String toolName;
    private String action;
    private String status;
    private Integer latencyMs;
    private String reason;
    private String inputSummary;
    private String outputSummary;
    private LocalDateTime createTime;
}
