package com.simon.MindCrew.service.knowledge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentQualityServiceTest {

    private final DocumentQualityService service = new DocumentQualityService();

    @Test
    void removesCommonExportNoiseAndKeepsQualityReport() {
        String raw = """
                Confluence Page ID: 12345
                部署手册

                服务部署需要先配置 MySQL、Redis 和 Milvus。
                上次编辑者：张三
                12345
                服务部署需要先配置 MySQL、Redis 和 Milvus。
                """;

        DocumentQualityService.CleanResult result = service.cleanAndAnalyze(raw, "deploy.md");

        assertFalse(result.cleanedText().contains("Confluence Page ID"));
        assertFalse(result.cleanedText().contains("上次编辑者"));
        assertTrue(result.cleanedText().contains("服务部署"));
        assertTrue(result.report().noiseLinesRemoved() >= 2);
        assertTrue(result.report().qualityScore() > 0);
    }
}
