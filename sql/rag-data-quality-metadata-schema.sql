-- MindCrew RAG data quality + semantic metadata enhancement
-- Adds production-grade ingestion diagnostics and answerable question metadata.

SET @schema_name = DATABASE();

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `kb_knowledge_base` ADD COLUMN `answerable_questions` JSON NULL COMMENT ''文档可回答问题数组'' AFTER `summary`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'kb_knowledge_base'
    AND COLUMN_NAME = 'answerable_questions'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `kb_knowledge_base` ADD COLUMN `quality_report` JSON NULL COMMENT ''文档清洗质量报告'' AFTER `answerable_questions`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'kb_knowledge_base'
    AND COLUMN_NAME = 'quality_report'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Optional but useful for parent/neighbor chunk recall.
SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX `idx_kb_chunk_parent_window` ON `kb_chunk` (`kb_id`, `chunk_index`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'kb_chunk'
    AND INDEX_NAME = 'idx_kb_chunk_parent_window'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
