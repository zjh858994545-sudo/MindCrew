package com.simon.MindCrew.service.knowledge;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量级语义元数据增强。
 *
 * 生产环境可以替换为 LLM 批量增强；这里先用稳定的本地规则产出
 * summary / keywords / answerableQuestions，让入库和面试演示都可离线复现。
 */
@Service
public class DocumentMetadataEnhancer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9_@./:-]{2,32}");
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^\\s{0,3}(#{1,6}\\s*)?([\\p{IsHan}A-Za-z0-9_ .:/-]{3,60})\\s*$");
    private static final Set<String> STOP_WORDS = Set.of(
            "这个", "一个", "进行", "需要", "支持", "可以", "通过", "系统", "文档", "用户", "相关", "配置", "使用",
            "the", "and", "for", "with", "this", "that", "from"
    );

    public SemanticMetadata enhance(String fileName, String category, String cleanedText) {
        String text = cleanedText == null ? "" : cleanedText.trim();
        String title = titleFrom(fileName);
        String summary = buildSummary(text);
        List<String> keywords = extractKeywords(title, category, text);
        List<String> questions = buildQuestions(title, keywords, text);
        String embeddingPrefix = buildEmbeddingPrefix(title, summary, keywords, questions);
        return new SemanticMetadata(title, summary, keywords, questions, embeddingPrefix);
    }

    public Map<String, Object> toMetadataMap(SemanticMetadata metadata) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("documentTitle", metadata.title());
        out.put("documentSummary", metadata.summary());
        out.put("keywords", metadata.keywords());
        out.put("answerableQuestions", metadata.answerableQuestions());
        return out;
    }

    private String titleFrom(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "知识库文档";
        }
        String name = fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.replace('_', ' ').replace('-', ' ').trim();
    }

    private String buildSummary(String text) {
        if (text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 180) {
            return compact;
        }
        return compact.substring(0, 180) + "...";
    }

    private List<String> extractKeywords(String title, String category, String text) {
        Set<String> keywords = new LinkedHashSet<>();
        addKeyword(keywords, category);
        Matcher headingMatcher = HEADING_PATTERN.matcher(text);
        while (headingMatcher.find() && keywords.size() < 8) {
            addKeyword(keywords, headingMatcher.group(2));
        }
        Matcher matcher = TOKEN_PATTERN.matcher((title == null ? "" : title) + "\n" + text);
        Map<String, Integer> frequency = new LinkedHashMap<>();
        while (matcher.find()) {
            String token = normalize(matcher.group());
            if (isUseful(token)) {
                frequency.merge(token, 1, Integer::sum);
            }
        }
        frequency.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(24)
                .forEach(e -> addKeyword(keywords, e.getKey()));
        return keywords.stream().limit(8).toList();
    }

    private List<String> buildQuestions(String title, List<String> keywords, String text) {
        List<String> questions = new ArrayList<>();
        String subject = !keywords.isEmpty() ? keywords.get(0) : title;
        if (subject == null || subject.isBlank()) {
            subject = "这份文档";
        }
        questions.add("这份文档主要说明了" + subject + "的哪些核心内容？");
        if (containsAny(text, "配置", "参数", "部署", "安装")) {
            questions.add(subject + "如何配置或部署？");
        }
        if (containsAny(text, "步骤", "流程", "操作", "申请", "审批")) {
            questions.add(subject + "的处理流程和操作步骤是什么？");
        }
        if (containsAny(text, "风险", "禁止", "注意", "安全", "权限")) {
            questions.add(subject + "有哪些风险、权限或注意事项？");
        }
        if (questions.size() < 3 && title != null && !title.isBlank()) {
            questions.add("遇到" + title + "相关问题时应该如何排查？");
        }
        return questions.stream().distinct().limit(5).toList();
    }

    private String buildEmbeddingPrefix(String title, String summary, List<String> keywords, List<String> questions) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) sb.append("文档标题：").append(title).append('\n');
        if (summary != null && !summary.isBlank()) sb.append("文档摘要：").append(summary).append('\n');
        if (!keywords.isEmpty()) sb.append("关键词：").append(String.join("，", keywords)).append('\n');
        if (!questions.isEmpty()) sb.append("可回答问题：").append(String.join("；", questions)).append('\n');
        return sb.toString();
    }

    private void addKeyword(Set<String> keywords, String raw) {
        String token = normalize(raw);
        if (isUseful(token)) {
            keywords.add(token);
        }
    }

    private String normalize(String token) {
        if (token == null) return "";
        return token.trim()
                .replaceAll("^[#\\s:：·\\-]+", "")
                .replaceAll("[\\s:：·\\-]+$", "")
                .toLowerCase(Locale.ROOT);
    }

    private boolean isUseful(String token) {
        if (token == null || token.isBlank()) return false;
        if (STOP_WORDS.contains(token)) return false;
        if (token.chars().allMatch(Character::isDigit)) return false;
        return token.length() >= 2 && token.length() <= 32;
    }

    private boolean containsAny(String text, String... words) {
        if (text == null) return false;
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    public record SemanticMetadata(String title,
                                   String summary,
                                   List<String> keywords,
                                   List<String> answerableQuestions,
                                   String embeddingPrefix) {}
}
