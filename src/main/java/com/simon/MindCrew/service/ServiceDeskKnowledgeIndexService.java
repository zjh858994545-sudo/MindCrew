package com.simon.MindCrew.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.simon.MindCrew.entity.KbChunk;
import com.simon.MindCrew.entity.KbKnowledgeBase;
import com.simon.MindCrew.mapper.KbChunkMapper;
import com.simon.MindCrew.mapper.KbKnowledgeBaseMapper;
import com.simon.MindCrew.service.knowledge.MilvusService;
import com.simon.MindCrew.service.knowledge.TextChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceDeskKnowledgeIndexService {

    private static final String SERVICE_DESK_KB_CATEGORY = "service_desk";

    private final KbKnowledgeBaseMapper knowledgeBaseMapper;
    private final KbChunkMapper chunkMapper;
    private final MilvusService milvusService;
    private final EmbeddingModel embeddingModel;

    public ReindexReport reindexServiceDeskKnowledge() {
        List<KnowledgeBaseResult> results = new ArrayList<>();
        List<KbKnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectList(new QueryWrapper<KbKnowledgeBase>()
                .eq("category", SERVICE_DESK_KB_CATEGORY)
                .eq("status", "ready")
                .eq("deleted", 0)
                .orderByAsc("id"));

        if (knowledgeBases.isEmpty()) {
            return new ReindexReport(false, 0, 0, 0, results,
                    "No ready service_desk knowledge base found. Run sql/service-desk-loop-schema.sql first.");
        }

        milvusService.initCollection();
        int chunkCount = 0;
        int indexedCount = 0;
        int failedCount = 0;

        for (KbKnowledgeBase kb : knowledgeBases) {
            List<KbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                    .eq(KbChunk::getKbId, kb.getId())
                    .orderByAsc(KbChunk::getChunkIndex));
            chunkCount += chunks.size();

            try {
                List<TextChunker.TextChunk> textChunks = toTextChunks(kb, chunks);
                List<List<Float>> embeddings = embed(textChunks);
                milvusService.deleteByKnowledgeBaseId(kb.getId());
                milvusService.insertVectors(textChunks, embeddings);
                indexedCount += textChunks.size();
                results.add(new KnowledgeBaseResult(kb.getId(), kb.getName(), true,
                        textChunks.size(), textChunks.size(), "indexed"));
            } catch (Exception ex) {
                failedCount++;
                results.add(new KnowledgeBaseResult(kb.getId(), kb.getName(), false,
                        chunks.size(), 0, safeMessage(ex)));
                log.warn("[ServiceDeskKnowledgeIndex] reindex failed kbId={} reason={}", kb.getId(), ex.getMessage());
            }
        }

        boolean success = failedCount == 0;
        String message = success
                ? "Service desk knowledge vectors rebuilt."
                : "Some service desk knowledge bases failed. Check model key, Milvus, and chunk metadata.";
        return new ReindexReport(success, knowledgeBases.size(), chunkCount, indexedCount, results, message);
    }

    private List<TextChunker.TextChunk> toTextChunks(KbKnowledgeBase kb, List<KbChunk> chunks) {
        List<TextChunker.TextChunk> out = new ArrayList<>();
        for (KbChunk source : chunks) {
            if (source.getContent() == null || source.getContent().isBlank()) {
                continue;
            }
            JSONObject metadata = parseMetadata(source.getMetadata());
            TextChunker.TextChunk chunk = new TextChunker.TextChunk();
            chunk.setContent(source.getContent());
            chunk.setChunkIndex(source.getChunkIndex() == null ? out.size() : source.getChunkIndex());
            chunk.setKnowledgeBaseId(kb.getId());
            chunk.setCategory(kb.getCategory());
            chunk.setContentType(firstText(metadata.getString("contentType"), "service_desk"));
            chunk.setChapter(firstText(metadata.getString("chapter"), kb.getName()));
            Integer pageNumber = metadata.getInteger("pageNumber");
            chunk.setPageNumber(pageNumber == null ? 0 : pageNumber);
            out.add(chunk);
        }
        return out;
    }

    private List<List<Float>> embed(List<TextChunker.TextChunk> chunks) {
        List<List<Float>> embeddings = new ArrayList<>(chunks.size());
        for (TextChunker.TextChunk chunk : chunks) {
            float[] vector = embeddingModel.embed(chunk.getContent());
            List<Float> floats = new ArrayList<>(vector.length);
            for (float value : vector) {
                floats.add(value);
            }
            embeddings.add(floats);
        }
        return embeddings;
    }

    private JSONObject parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(metadata);
        } catch (Exception ex) {
            return new JSONObject();
        }
    }

    private static String firstText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String safeMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null) {
            return "unknown error";
        }
        return ex.getMessage().substring(0, Math.min(500, ex.getMessage().length()));
    }

    public record ReindexReport(boolean success,
                                int knowledgeBaseCount,
                                int chunkCount,
                                int indexedCount,
                                List<KnowledgeBaseResult> results,
                                String message) {
    }

    public record KnowledgeBaseResult(Long knowledgeBaseId,
                                      String knowledgeBaseName,
                                      boolean success,
                                      int chunkCount,
                                      int indexedCount,
                                      String message) {
    }
}
