package com.simon.MindCrew.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史知识库文档实体
 */
@Data
@TableName("kb_knowledge_base")
public class MedKnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String category;

    /** LLM/规则提取的标签数组 JSON */
    private String tags;

    /** 文档摘要 */
    private String summary;

    /** 是否用户手动指定分类 */
    private Integer categoryUserSet;

    /** 文档可回答问题数组 JSON */
    private String answerableQuestions;

    /** 文档清洗质量报告 JSON */
    private String qualityReport;

    private String fileUrl;

    private String fileType;

    private Long fileSize;

    private Integer chunkCount;

    /** 状态: uploading/processing/ready/failed */
    private String status;

    /** 可见性 · 任务 7（public / scoped / private） */
    private String visibility;

    private String errorMsg;

    /** 创建者用户ID */
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
