package com.simon.MindCrew.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_eval_case")
public class RagEvalCase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private String question;
    private String expectedAnswer;
    private String expectedChunkIds;
    private String expectedKeywords;
    private String category;
    private String difficulty;
    private Integer shouldRefuse;
    private LocalDateTime createTime;
}
