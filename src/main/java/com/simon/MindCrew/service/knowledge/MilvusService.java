package com.simon.MindCrew.service.knowledge;

import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.*;
import io.milvus.param.index.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Milvus 向量数据库服务
 * 负责 Collection 初始化、向量插入、向量检索、向量删除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusService {

    private final MilvusServiceClient milvusClient;

    @Value("${milvus.collection-name}")
    private String collectionName;

    @Value("${milvus.dimension}")
    private Integer dimension;

    /**
     * 初始化 Collection（建表）
     */
    public void initCollection() {
        try {
            R<Boolean> hasCollection = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder().withCollectionName(collectionName).build());

            if (Boolean.TRUE.equals(hasCollection.getData())) {
                log.info("Milvus Collection 已存在: {}", collectionName);
                return;
            }

            // 定义字段
            FieldType idField = FieldType.newBuilder()
                    .withName("id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .build();

            FieldType embeddingField = FieldType.newBuilder()
                    .withName("embedding")
                    .withDataType(DataType.FloatVector)
                    .withDimension(dimension)
                    .build();

            FieldType kbIdField = FieldType.newBuilder()
                    .withName("knowledge_base_id")
                    .withDataType(DataType.Int64)
                    .build();

            FieldType categoryField = FieldType.newBuilder()
                    .withName("category")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(50)
                    .build();

            FieldType contentTypeField = FieldType.newBuilder()
                    .withName("content_type")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(30)
                    .build();

            FieldType contentField = FieldType.newBuilder()
                    .withName("content")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(4096)
                    .build();

            FieldType chapterField = FieldType.newBuilder()
                    .withName("chapter")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(200)
                    .build();

            FieldType pageField = FieldType.newBuilder()
                    .withName("page_number")
                    .withDataType(DataType.Int64)
                    .build();

            // 创建 Collection
            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDescription("MindCrew 通用知识切片向量库")
                    .withShardsNum(2)
                    .addFieldType(idField)
                    .addFieldType(embeddingField)
                    .addFieldType(kbIdField)
                    .addFieldType(categoryField)
                    .addFieldType(contentTypeField)
                    .addFieldType(contentField)
                    .addFieldType(chapterField)
                    .addFieldType(pageField)
                    .build();

            R<RpcStatus> createResult = milvusClient.createCollection(createParam);
            if (createResult.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("创建Collection失败: " + createResult.getMessage());
            }

            // 创建向量索引 (HNSW)
            IndexType indexType = IndexType.HNSW;
            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName("embedding")
                    .withIndexType(indexType)
                    .withMetricType(MetricType.COSINE)
                    .withExtraParam("{\"M\": 16, \"efConstruction\": 64}")
                    .build();

            milvusClient.createIndex(indexParam);

            // 加载到内存
            milvusClient.loadCollection(
                    LoadCollectionParam.newBuilder().withCollectionName(collectionName).build());

            log.info("Milvus Collection 创建成功: {}", collectionName);
        } catch (Exception e) {
            log.warn("Milvus 初始化失败（请确保 Milvus 已启动）: {}", e.getMessage());
        }
    }

    /**
     * 批量插入向量
     * @param chunks 文本切片列表
     * @param embeddings 对应的向量列表
     */
    public void insertVectors(List<TextChunker.TextChunk> chunks, List<List<Float>> embeddings) {
        if (chunks.isEmpty()) return;

        List<String> ids = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();
        List<Long> kbIds = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        List<String> contentTypes = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<String> chapters = new ArrayList<>();
        List<Long> pageNumbers = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            TextChunker.TextChunk chunk = chunks.get(i);
            ids.add(UUID.randomUUID().toString().replace("-", ""));
            vectors.add(embeddings.get(i));
            kbIds.add(chunk.getKnowledgeBaseId());
            categories.add(nullToEmpty(chunk.getCategory()));
            contentTypes.add(nullToEmpty(chunk.getContentType()));
            // 内容截断防超长
            String content = chunk.getContent();
            contents.add(content.length() > 4000 ? content.substring(0, 4000) : content);
            chapters.add(nullToEmpty(chunk.getChapter()));
            pageNumbers.add((long) chunk.getPageNumber());
        }

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(Arrays.asList(
                        new InsertParam.Field("id", ids),
                        new InsertParam.Field("embedding", vectors),
                        new InsertParam.Field("knowledge_base_id", kbIds),
                        new InsertParam.Field("category", categories),
                        new InsertParam.Field("content_type", contentTypes),
                        new InsertParam.Field("content", contents),
                        new InsertParam.Field("chapter", chapters),
                        new InsertParam.Field("page_number", pageNumbers)
                ))
                .build();

        R<MutationResult> result = milvusClient.insert(insertParam);
        if (result.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("向量插入失败: " + result.getMessage());
        }
        milvusClient.flush(FlushParam.newBuilder().withCollectionNames(List.of(collectionName)).build());
        log.info("向量插入成功: {}条", chunks.size());
    }

    /**
     * 按知识库ID删除所有向量
     */
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        try {
            String expr = "knowledge_base_id == " + knowledgeBaseId;
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .build();
            milvusClient.delete(deleteParam);
            log.info("已删除知识库 {} 的所有向量", knowledgeBaseId);
        } catch (Exception e) {
            log.warn("向量删除失败: {}", e.getMessage());
        }
    }

    /**
     * 向量语义检索（Top-K）
     */
    public List<SearchResult> search(List<Float> queryVector, String categoryFilter,
                                      int topK) {
        if (queryVector == null || queryVector.isEmpty()) {
            return new ArrayList<>();
        }
        if (dimension != null && queryVector.size() != dimension) {
            log.warn("Milvus search skipped: query vector dimension={} expected={}", queryVector.size(), dimension);
            return new ArrayList<>();
        }

        try {
            SearchParam.Builder builder = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(MetricType.COSINE)
                    .withOutFields(Arrays.asList("id", "content", "category", "content_type",
                            "chapter", "page_number", "knowledge_base_id"))
                    .withTopK(Math.max(1, topK))
                    .withVectors(List.of(queryVector))
                    .withVectorFieldName("embedding")
                    .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                    .withParams("{\"ef\": 64}");

            String filter = buildFilterExpression(categoryFilter);
            if (filter != null) {
                builder.withExpr(filter);
            }

            R<SearchResults> response = milvusClient.search(builder.build());
            if (response.getStatus() != R.Status.Success.getCode()) {
                log.warn("Milvus search failed: {}", response.getMessage());
                return new ArrayList<>();
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
            List<SearchResult> results = new ArrayList<>(scores.size());

            for (int i = 0; i < scores.size(); i++) {
                SearchResultsWrapper.IDScore score = scores.get(i);
                results.add(new SearchResult(
                        scoreId(score),
                        score.getScore(),
                        fieldValue(wrapper, "content", i),
                        fieldValue(wrapper, "category", i),
                        fieldValue(wrapper, "content_type", i),
                        fieldValue(wrapper, "chapter", i),
                        intFieldValue(wrapper, "page_number", i),
                        longFieldValue(wrapper, "knowledge_base_id", i)
                ));
            }
            log.info("Milvus search success: topK={} hits={}", topK, results.size());
            return results;
        } catch (Exception ex) {
            log.warn("Milvus search exception: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String buildFilterExpression(String categoryFilter) {
        if (categoryFilter == null || categoryFilter.isBlank()) {
            return null;
        }
        return "category == \"" + categoryFilter.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String fieldValue(SearchResultsWrapper wrapper, String fieldName, int index) {
        try {
            List<?> values = wrapper.getFieldData(fieldName, 0);
            if (values != null && index < values.size() && values.get(index) != null) {
                return values.get(index).toString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private int intFieldValue(SearchResultsWrapper wrapper, String fieldName, int index) {
        String value = fieldValue(wrapper, fieldName, index);
        try {
            return value.isBlank() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long longFieldValue(SearchResultsWrapper wrapper, String fieldName, int index) {
        String value = fieldValue(wrapper, fieldName, index);
        try {
            return value.isBlank() ? 0L : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String scoreId(SearchResultsWrapper.IDScore score) {
        try {
            String id = score.getStrID();
            if (id != null && !id.isBlank()) {
                return id;
            }
        } catch (Exception ignored) {}
        return String.valueOf(score.getLongID());
    }

    /**
     * 检索结果记录
     */
    public record SearchResult(String id, float score, String content, String category,
                                String contentType, String chapter, int pageNumber, long knowledgeBaseId) {}
}
