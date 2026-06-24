package com.simon.MindCrew.service.knowledge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentMetadataEnhancerTest {

    private final DocumentMetadataEnhancer enhancer = new DocumentMetadataEnhancer();

    @Test
    void generatesSummaryKeywordsAndAnswerableQuestions() {
        DocumentMetadataEnhancer.SemanticMetadata metadata = enhancer.enhance(
                "生产权限申请SOP.md",
                "security",
                """
                # 生产权限申请流程
                申请生产权限必须经过直属负责人和安全负责人审批。
                操作步骤包括提交申请、说明用途、限定时间窗口、记录审计日志。
                注意事项：禁止共享账号，禁止导出敏感数据。
                """
        );

        assertFalse(metadata.summary().isBlank());
        assertFalse(metadata.keywords().isEmpty());
        assertFalse(metadata.answerableQuestions().isEmpty());
        assertTrue(metadata.embeddingPrefix().contains("可回答问题"));
    }
}
