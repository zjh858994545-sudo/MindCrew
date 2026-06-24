package com.simon.MindCrew.service.knowledge;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文档清洗质量分析。
 *
 * 目标不是把所有内容都“改写干净”，而是在入库前留下可解释的质量报告：
 * 原文多长、移除了多少噪音行、是否存在超长段落、最终质量分是多少。
 */
@Service
public class DocumentQualityService {

    private static final Pattern NOISE_LINE = Pattern.compile(
            "(?i).*(confluence|page\\s*id|页面\\s*id|编辑历史|上次编辑者|最后编辑|下载次数|附件下载|回复区|浏览器不支持\\s*video|browser does not support).*");
    private static final Pattern PURE_NUMBER_OR_SYMBOL = Pattern.compile("^[\\d\\s\\-_=+*/#|:：.。·,，;；()（）\\[\\]【】]+$");
    private static final int LONG_PARAGRAPH_THRESHOLD = 1800;

    public CleanResult cleanAndAnalyze(String rawText, String fileName) {
        String raw = rawText == null ? "" : rawText;
        String normalized = raw
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('　', ' ');

        String[] lines = normalized.split("\n", -1);
        List<String> kept = new ArrayList<>();
        Set<String> recentLines = new LinkedHashSet<>();

        int blankLines = 0;
        int collapsedBlankLines = 0;
        int noiseLines = 0;
        int duplicateLines = 0;
        int pureNumberLines = 0;
        boolean previousBlank = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                blankLines++;
                if (previousBlank) {
                    collapsedBlankLines++;
                    continue;
                }
                kept.add("");
                previousBlank = true;
                continue;
            }
            previousBlank = false;

            if (NOISE_LINE.matcher(trimmed).matches()) {
                noiseLines++;
                continue;
            }
            if (trimmed.length() <= 32 && PURE_NUMBER_OR_SYMBOL.matcher(trimmed).matches()) {
                pureNumberLines++;
                continue;
            }
            if (trimmed.length() <= 120 && recentLines.contains(trimmed)) {
                duplicateLines++;
                continue;
            }

            kept.add(line.stripTrailing());
            recentLines.add(trimmed);
            if (recentLines.size() > 80) {
                recentLines.remove(recentLines.iterator().next());
            }
        }

        String cleaned = String.join("\n", kept)
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        int longParagraphs = countLongParagraphs(cleaned);
        int cleanedChars = cleaned.length();
        int removedLines = noiseLines + duplicateLines + pureNumberLines + collapsedBlankLines;
        int score = score(raw.length(), cleanedChars, removedLines, longParagraphs);

        List<String> warnings = new ArrayList<>();
        if (cleanedChars < 80) warnings.add("cleaned_text_too_short");
        if (longParagraphs > 0) warnings.add("long_paragraph_detected");
        if (noiseLines > 0) warnings.add("noise_lines_removed");
        if (duplicateLines > 0) warnings.add("duplicate_lines_removed");

        QualityReport report = new QualityReport(
                fileName,
                raw.length(),
                cleanedChars,
                lines.length,
                kept.size(),
                blankLines,
                collapsedBlankLines,
                noiseLines,
                duplicateLines,
                pureNumberLines,
                longParagraphs,
                score,
                warnings
        );
        return new CleanResult(cleaned, report);
    }

    public Map<String, Object> toJsonMap(QualityReport report, int chunkCount, int maxChunkLength, int avgChunkLength) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileName", report.fileName());
        out.put("originalChars", report.originalChars());
        out.put("cleanedChars", report.cleanedChars());
        out.put("originalLines", report.originalLines());
        out.put("keptLines", report.keptLines());
        out.put("blankLines", report.blankLines());
        out.put("collapsedBlankLines", report.collapsedBlankLines());
        out.put("noiseLinesRemoved", report.noiseLinesRemoved());
        out.put("duplicateLinesRemoved", report.duplicateLinesRemoved());
        out.put("pureNumberLinesRemoved", report.pureNumberLinesRemoved());
        out.put("longParagraphs", report.longParagraphs());
        out.put("chunkCount", chunkCount);
        out.put("maxChunkLength", maxChunkLength);
        out.put("avgChunkLength", avgChunkLength);
        out.put("qualityScore", report.qualityScore());
        out.put("warnings", report.warnings());
        return out;
    }

    private int countLongParagraphs(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String paragraph : text.split("\\n\\s*\\n")) {
            if (paragraph.trim().length() > LONG_PARAGRAPH_THRESHOLD) {
                count++;
            }
        }
        return count;
    }

    private int score(int originalChars, int cleanedChars, int removedLines, int longParagraphs) {
        int score = 100;
        if (originalChars == 0 || cleanedChars == 0) {
            return 0;
        }
        double removedRatio = Math.max(0, originalChars - cleanedChars) / (double) Math.max(1, originalChars);
        if (removedRatio > 0.35) score -= 20;
        else if (removedRatio > 0.18) score -= 10;
        score -= Math.min(25, removedLines / 5);
        score -= Math.min(20, longParagraphs * 4);
        if (cleanedChars < 300) score -= 15;
        return Math.max(0, Math.min(100, score));
    }

    public record CleanResult(String cleanedText, QualityReport report) {}

    public record QualityReport(String fileName,
                                int originalChars,
                                int cleanedChars,
                                int originalLines,
                                int keptLines,
                                int blankLines,
                                int collapsedBlankLines,
                                int noiseLinesRemoved,
                                int duplicateLinesRemoved,
                                int pureNumberLinesRemoved,
                                int longParagraphs,
                                int qualityScore,
                                List<String> warnings) {}
}
