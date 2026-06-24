package com.simon.MindCrew.service.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.simon.MindCrew.entity.KbChunk;
import com.simon.MindCrew.mapper.KbChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 父子/邻接 chunk 召回。
 *
 * 命中某个 chunk 后，补回同一文档的相邻 chunk；小文档直接补全文档。
 * 解决“命中了摘要切片，但答案在相邻正文切片里”的生产常见问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextWindowExpander {

    private static final int WINDOW = 1;
    private static final int SMALL_DOCUMENT_CHUNK_LIMIT = 6;
    private static final int MAX_ADDED_CHUNKS = 8;

    private final KbChunkMapper kbChunkMapper;

    public ExpansionResult expand(List<RetrievedChunk> rankedChunks) {
        if (rankedChunks == null || rankedChunks.isEmpty()) {
            return new ExpansionResult(List.of(), 0, 0);
        }

        List<RetrievedChunk> expanded = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int added = 0;
        int parentDocs = 0;

        for (RetrievedChunk chunk : rankedChunks) {
            if (added >= MAX_ADDED_CHUNKS) {
                addIfAbsent(expanded, seen, chunk);
                continue;
            }

            KbChunk anchor = resolveAnchor(chunk);
            if (anchor != null && anchor.getKbId() != null && anchor.getChunkIndex() != null) {
                chunk.setKnowledgeBaseId(anchor.getKbId());
                chunk.setChunkIndex(anchor.getChunkIndex());
            }

            addIfAbsent(expanded, seen, chunk);

            if (anchor == null || anchor.getKbId() == null || anchor.getChunkIndex() == null) {
                continue;
            }

            List<KbChunk> siblings = loadSiblingChunks(anchor);
            if (siblings.isEmpty()) {
                continue;
            }
            parentDocs++;
            for (KbChunk sibling : siblings) {
                if (added >= MAX_ADDED_CHUNKS) {
                    break;
                }
                RetrievedChunk context = toRetrievedChunk(sibling, chunk);
                if (addIfAbsent(expanded, seen, context)) {
                    added++;
                }
            }
        }

        log.debug("[ContextWindow] input={} output={} added={} parentDocs={}",
                rankedChunks.size(), expanded.size(), added, parentDocs);
        return new ExpansionResult(expanded, added, parentDocs);
    }

    private KbChunk resolveAnchor(RetrievedChunk chunk) {
        Long numericId = parseLong(chunk.getId());
        if (numericId != null) {
            KbChunk byId = kbChunkMapper.selectById(numericId);
            if (byId != null) return byId;
        }
        if (chunk.getKnowledgeBaseId() == null || chunk.getContent() == null || chunk.getContent().isBlank()) {
            return null;
        }
        return kbChunkMapper.selectOne(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, chunk.getKnowledgeBaseId())
                .eq(KbChunk::getContent, chunk.getContent())
                .last("LIMIT 1"));
    }

    private List<KbChunk> loadSiblingChunks(KbChunk anchor) {
        Long count = kbChunkMapper.selectCount(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, anchor.getKbId()));
        LambdaQueryWrapper<KbChunk> wrapper = new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, anchor.getKbId())
                .orderByAsc(KbChunk::getChunkIndex);
        if (count == null || count > SMALL_DOCUMENT_CHUNK_LIMIT) {
            int left = Math.max(0, anchor.getChunkIndex() - WINDOW);
            int right = anchor.getChunkIndex() + WINDOW;
            wrapper.between(KbChunk::getChunkIndex, left, right);
        }
        return kbChunkMapper.selectList(wrapper);
    }

    private RetrievedChunk toRetrievedChunk(KbChunk sibling, RetrievedChunk anchor) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setId(String.valueOf(sibling.getId()));
        chunk.setKnowledgeBaseId(sibling.getKbId());
        chunk.setChunkIndex(sibling.getChunkIndex());
        chunk.setContent(sibling.getContent());
        chunk.setScore(anchor.getScore() * 0.82f);
        chunk.setRerankScore(anchor.getRerankScore() * 0.82f);
        chunk.setSource(RetrievedChunk.Source.HYBRID);
        chunk.setSourceName(anchor.getSourceName());
        chunk.setSourceRef("parent_context");
        applyMetadata(chunk, sibling.getMetadata());
        return chunk;
    }

    private void applyMetadata(RetrievedChunk chunk, String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) return;
        try {
            JSONObject metadata = JSON.parseObject(metadataJson);
            chunk.setContentType(metadata.getString("contentType"));
            chunk.setChapter(metadata.getString("chapter"));
            Integer pageNumber = metadata.getInteger("pageNumber");
            if (pageNumber != null) chunk.setPageNumber(pageNumber);
            Long startMs = metadata.getLong("startMs");
            Long endMs = metadata.getLong("endMs");
            if (startMs != null) chunk.setStartMs(startMs);
            if (endMs != null) chunk.setEndMs(endMs);
            chunk.setSpeakerId(metadata.getString("speakerId"));
            chunk.setSourceObjectName(metadata.getString("sourceObjectName"));
        } catch (Exception ignored) {
            // Metadata failure should never break retrieval.
        }
    }

    private boolean addIfAbsent(List<RetrievedChunk> chunks, Set<String> seen, RetrievedChunk chunk) {
        String key = keyOf(chunk);
        if (!seen.add(key)) {
            return false;
        }
        chunks.add(chunk);
        return true;
    }

    private String keyOf(RetrievedChunk chunk) {
        if (chunk.getKnowledgeBaseId() != null && chunk.getChunkIndex() != null) {
            return chunk.getKnowledgeBaseId() + ":" + chunk.getChunkIndex();
        }
        if (chunk.getId() != null && !chunk.getId().isBlank()) {
            return "id:" + chunk.getId();
        }
        return "content:" + Objects.toString(chunk.getContent(), "");
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record ExpansionResult(List<RetrievedChunk> chunks, int addedChunks, int parentDocuments) {}
}
