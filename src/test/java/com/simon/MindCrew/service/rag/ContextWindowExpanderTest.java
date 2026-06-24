package com.simon.MindCrew.service.rag;

import com.simon.MindCrew.entity.KbChunk;
import com.simon.MindCrew.mapper.KbChunkMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextWindowExpanderTest {

    @Test
    void expandsNeighborChunksForMatchedChunk() {
        KbChunkMapper mapper = mock(KbChunkMapper.class);
        KbChunk anchor = chunk(2L, 10L, 1, "参数说明");
        when(mapper.selectById(2L)).thenReturn(anchor);
        when(mapper.selectCount(any())).thenReturn(3L);
        when(mapper.selectList(any())).thenReturn(List.of(
                chunk(1L, 10L, 0, "部署前准备"),
                anchor,
                chunk(3L, 10L, 2, "排障步骤")
        ));

        RetrievedChunk hit = new RetrievedChunk();
        hit.setId("2");
        hit.setKnowledgeBaseId(10L);
        hit.setChunkIndex(1);
        hit.setContent("参数说明");
        hit.setScore(0.9f);
        hit.setRerankScore(0.9f);
        hit.setSource(RetrievedChunk.Source.BM25);

        ContextWindowExpander.ExpansionResult result = new ContextWindowExpander(mapper).expand(List.of(hit));

        assertEquals(3, result.chunks().size());
        assertEquals(2, result.addedChunks());
        assertTrue(result.chunks().stream().anyMatch(c -> "部署前准备".equals(c.getContent())));
        assertTrue(result.chunks().stream().anyMatch(c -> "排障步骤".equals(c.getContent())));
    }

    private KbChunk chunk(Long id, Long kbId, Integer index, String content) {
        KbChunk chunk = new KbChunk();
        chunk.setId(id);
        chunk.setKbId(kbId);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setMetadata("{\"contentType\":\"procedure\",\"pageNumber\":0}");
        return chunk;
    }
}
