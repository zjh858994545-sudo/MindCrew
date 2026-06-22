package com.simon.MindCrew.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.simon.MindCrew.entity.KbChunk;
import com.simon.MindCrew.entity.KbKnowledgeBase;
import com.simon.MindCrew.mapper.KbChunkMapper;
import com.simon.MindCrew.mapper.KbKnowledgeBaseMapper;
import com.simon.MindCrew.service.knowledge.MilvusService;
import com.simon.MindCrew.service.knowledge.TextChunker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceDeskKnowledgeIndexServiceTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void reindexBuildsEmbeddingsAndWritesMilvusVectors() {
        KbKnowledgeBaseMapper kbMapper = mock(KbKnowledgeBaseMapper.class);
        KbChunkMapper chunkMapper = mock(KbChunkMapper.class);
        MilvusService milvusService = mock(MilvusService.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setId(10L);
        kb.setName("MindCrew Service Desk Knowledge");
        kb.setCategory("service_desk");
        when(kbMapper.selectList(any(Wrapper.class))).thenReturn(List.of(kb));

        KbChunk chunk = new KbChunk();
        chunk.setId(100L);
        chunk.setKbId(10L);
        chunk.setChunkIndex(1);
        chunk.setContent("Security policy: raw logs require masking and approval.");
        chunk.setMetadata("""
                {"chapter":"Security-Data-Export-Policy","pageNumber":5,"contentType":"security"}
                """);
        when(chunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(chunk));

        when(embeddingModel.embed(any(String.class))).thenReturn(vector(1024));

        ServiceDeskKnowledgeIndexService service = new ServiceDeskKnowledgeIndexService(
                kbMapper, chunkMapper, milvusService, embeddingModel);

        ServiceDeskKnowledgeIndexService.ReindexReport report = service.reindexServiceDeskKnowledge();

        assertTrue(report.success());
        assertEquals(1, report.knowledgeBaseCount());
        assertEquals(1, report.chunkCount());
        assertEquals(1, report.indexedCount());

        ArgumentCaptor<List> chunkCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> embeddingCaptor = ArgumentCaptor.forClass(List.class);
        verify(milvusService).deleteByKnowledgeBaseId(10L);
        verify(milvusService).insertVectors(chunkCaptor.capture(), embeddingCaptor.capture());

        List<TextChunker.TextChunk> textChunks = chunkCaptor.getValue();
        assertEquals(10L, textChunks.get(0).getKnowledgeBaseId());
        assertEquals("service_desk", textChunks.get(0).getCategory());
        assertEquals("Security-Data-Export-Policy", textChunks.get(0).getChapter());
        assertEquals(5, textChunks.get(0).getPageNumber());
        assertEquals(1, embeddingCaptor.getValue().size());
    }

    private float[] vector(int size) {
        float[] vector = new float[size];
        for (int i = 0; i < size; i++) {
            vector[i] = 0.001f;
        }
        return vector;
    }
}
