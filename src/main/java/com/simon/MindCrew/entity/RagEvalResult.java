package com.simon.MindCrew.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("rag_eval_result")
public class RagEvalResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long caseId;
    private String strategy;
    private String answer;
    private String retrievedChunksJson;
    private Double recallAtK;
    private Double mrr;
    private Double hitAtK;
    private Double citationHit;
    private Double refusalCorrect;
    private Long latencyMs;
    private String errorMessage;
}
