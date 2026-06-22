package com.simon.MindCrew.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_trace_span")
public class AgentTraceSpan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String spanType;
    private String name;
    private String inputSummary;
    private String outputSummary;
    private Long latencyMs;
    private String status;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
