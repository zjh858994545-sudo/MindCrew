package com.simon.MindCrew.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_eval_dataset")
public class RagEvalDataset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private Long knowledgeBaseId;
    private Long createdBy;
    private LocalDateTime createTime;
}
