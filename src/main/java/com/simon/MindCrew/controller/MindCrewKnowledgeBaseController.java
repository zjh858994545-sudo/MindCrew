package com.simon.MindCrew.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.simon.MindCrew.common.result.Result;
import com.simon.MindCrew.entity.KbChunk;
import com.simon.MindCrew.entity.KbKnowledgeBase;
import com.simon.MindCrew.entity.vo.PageVO;
import com.simon.MindCrew.mapper.KbChunkMapper;
import com.simon.MindCrew.mapper.KbKnowledgeBaseMapper;
import com.simon.MindCrew.service.KnowledgeBaseService;
import com.simon.MindCrew.service.UserService;
import com.simon.MindCrew.service.knowledge.FileStorageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MindCrew v2 知识库管理控制器（使用 KbKnowledgeBase 新实体）
 * 旧的 KnowledgeBaseController (/api/knowledge) 保持不变
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/kb")
@RequiredArgsConstructor
public class MindCrewKnowledgeBaseController {

    private final KbKnowledgeBaseMapper kbKnowledgeBaseMapper;
    private final KbChunkMapper kbChunkMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final UserService userService;
    private final FileStorageService fileStorage;

    // ==================== 查询 ====================

    /**
     * 分页获取知识库列表
     * GET /api/v2/kb/list?page=1&size=10&category=xxx
     */
    @GetMapping("/list")
    public Result<PageVO<KbKnowledgeBase>> list(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "category", required = false) String category) {

        Page<KbKnowledgeBase> pageObj = new Page<>(page, size);
        kbKnowledgeBaseMapper.selectPage(pageObj, new LambdaQueryWrapper<KbKnowledgeBase>()
                .eq(KbKnowledgeBase::getDeleted, 0)
                .eq(StringUtils.isNotBlank(category), KbKnowledgeBase::getCategory, category)
                .orderByDesc(KbKnowledgeBase::getCreateTime));

        return Result.success(PageVO.of(pageObj));
    }

    /**
     * 获取知识库详情（含 chunk_count）
     * GET /api/v2/kb/{id}
     */
    @GetMapping("/{id}")
    public Result<KbDetailVO> getById(@PathVariable Long id) {
        KbKnowledgeBase kb = kbKnowledgeBaseMapper.selectById(id);
        if (kb == null || (kb.getDeleted() != null && kb.getDeleted() == 1)) {
            return Result.error("知识库不存在");
        }

        // 统计切片数量
        long chunkCount = kbChunkMapper.selectCount(
                new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId, id));

        KbDetailVO vo = new KbDetailVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setCategory(kb.getCategory());
        vo.setDescription(kb.getDescription());
        vo.setFileUrl(kb.getFileUrl());
        vo.setFileType(kb.getFileType());
        vo.setFileSize(kb.getFileSize());
        vo.setChunkCount((int) chunkCount);
        vo.setStatus(kb.getStatus());
        vo.setErrorMsg(kb.getErrorMsg());
        vo.setUserId(kb.getUserId());
        vo.setCreateTime(kb.getCreateTime() != null ? kb.getCreateTime().toString() : null);
        vo.setUpdateTime(kb.getUpdateTime() != null ? kb.getUpdateTime().toString() : null);

        return Result.success(vo);
    }

    /**
     * 删除知识库（软删除）
     * DELETE /api/v2/kb/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        KbKnowledgeBase kb = kbKnowledgeBaseMapper.selectById(id);
        if (kb == null || (kb.getDeleted() != null && kb.getDeleted() == 1)) {
            return Result.error("知识库不存在");
        }
        knowledgeBaseService.deleteById(id);
        log.info("[MindCrewKnowledgeBaseController] 知识库删除完成: id={}", id);
        return Result.success();
    }

    /**
     * 生成媒体文件预签名 URL（用于前端时间戳级溯源播放）
     * GET /api/v2/kb/media-url?objectName=asr-audio/uuid.mp3
     *
     * 返回有效期 7 天的 URL，前端 audio/video 标签直接拿来播放并 seek 到 startMs。
     */
    @GetMapping("/media-url")
    public Result<MediaUrlVO> mediaUrl(@RequestParam String objectName) {
        if (StringUtils.isBlank(objectName)) {
            return Result.error("objectName 不能为空");
        }
        try {
            String url = fileStorage.getFileUrl(objectName);
            MediaUrlVO vo = new MediaUrlVO();
            vo.setUrl(url);
            vo.setExpireSeconds(7L * 24 * 3600);
            return Result.success(vo);
        } catch (Exception e) {
            log.error("生成 media-url 失败 objectName={}", objectName, e);
            return Result.error("生成 URL 失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有分类列表
     * GET /api/v2/kb/categories
     */
    @GetMapping("/categories")
    public Result<List<String>> categories() {
        List<String> categories = kbKnowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KbKnowledgeBase>()
                        .eq(KbKnowledgeBase::getDeleted, 0)
                        .select(KbKnowledgeBase::getCategory)
                        .groupBy(KbKnowledgeBase::getCategory)
        ).stream()
                .map(KbKnowledgeBase::getCategory)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        return Result.success(categories);
    }

    /**
     * 更新知识库信息
     * PUT /api/v2/kb/{id}
     * body: {name, description, category}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @RequestBody KbUpdateDTO dto) {
        KbKnowledgeBase kb = kbKnowledgeBaseMapper.selectById(id);
        if (kb == null || (kb.getDeleted() != null && kb.getDeleted() == 1)) {
            return Result.error("知识库不存在");
        }

        if (StringUtils.isNotBlank(dto.getName())) {
            kb.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            kb.setDescription(dto.getDescription());
        }
        if (StringUtils.isNotBlank(dto.getCategory())) {
            kb.setCategory(dto.getCategory());
        }

        kbKnowledgeBaseMapper.updateById(kb);
        log.info("[MindCrewKnowledgeBaseController] 知识库更新: id={}", id);
        return Result.success();
    }

    // ==================== 上传 ====================

    /**
     * 上传文件并创建知识库（新实体路径）
     * POST /api/v2/kb/upload
     * params: file, name, category, description
     */
    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Long> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam("category") String category,
            @RequestParam(value = "description", required = false) String description) {

        Long userId = userService.getCurrentUserId();
        String originalName = file.getOriginalFilename();
        String finalName = StringUtils.isNotBlank(name) ? name : originalName;
        Long kbId = knowledgeBaseService.uploadDocument(file, finalName, category, description, userId);

        log.info("[MindCrewKnowledgeBaseController] 知识库上传: kbId={}, name={}", kbId, finalName);
        return Result.success("上传成功，正在后台处理...", kbId);
    }

    // ==================== VO / DTO ====================

    @Data
    public static class KbDetailVO {
        private Long id;
        private String name;
        private String category;
        private String description;
        private String fileUrl;
        private String fileType;
        private Long fileSize;
        private Integer chunkCount;
        private String status;
        private String errorMsg;
        private Long userId;
        private String createTime;
        private String updateTime;
    }

    @Data
    public static class KbUpdateDTO {
        private String name;
        private String description;
        private String category;
    }

    @Data
    public static class MediaUrlVO {
        private String url;
        private Long expireSeconds;
    }
}
