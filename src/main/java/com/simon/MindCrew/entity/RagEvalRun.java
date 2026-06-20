package com.simon.MindCrew.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_eval_run")
public class RagEvalRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private String runId;
    private String strategy;
    private String modelName;
    private Integer topK;
    private Integer rerankEnabled;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String summaryJson;
    private String reportPath;
}
