package com.simon.MindCrew.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_trace")
public class AgentTrace {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private Long conversationId;
    private String userId;
    private String question;
    private String answer;
    private String status;
    private Long totalLatencyMs;
    private String modelName;
    private LocalDateTime createTime;
}
