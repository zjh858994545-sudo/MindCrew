-- MindCrew RAG Eval schema

CREATE TABLE IF NOT EXISTS `rag_eval_dataset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(128) NOT NULL COMMENT '数据集名称',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '说明',
  `knowledge_base_id` BIGINT DEFAULT NULL COMMENT '关联知识库',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 评测数据集';

CREATE TABLE IF NOT EXISTS `rag_eval_case` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dataset_id` BIGINT NOT NULL COMMENT '数据集 ID',
  `question` TEXT NOT NULL COMMENT '问题',
  `expected_answer` TEXT COMMENT '期望答案',
  `expected_chunk_ids` TEXT COMMENT '期望命中文档切片 ID，逗号分隔或 JSON',
  `expected_keywords` TEXT COMMENT '期望关键词',
  `category` VARCHAR(64) DEFAULT NULL COMMENT '分类',
  `difficulty` VARCHAR(32) DEFAULT NULL COMMENT '难度',
  `should_refuse` TINYINT DEFAULT 0 COMMENT '是否应拒答',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_dataset` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 评测 Case';

CREATE TABLE IF NOT EXISTS `rag_eval_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dataset_id` BIGINT DEFAULT NULL COMMENT '数据集 ID',
  `run_id` VARCHAR(80) NOT NULL COMMENT '评测运行 ID',
  `strategy` VARCHAR(64) DEFAULT NULL COMMENT '策略，批量对比时为空',
  `model_name` VARCHAR(128) DEFAULT NULL COMMENT '模型名称',
  `top_k` INT DEFAULT 5 COMMENT 'TopK',
  `rerank_enabled` TINYINT DEFAULT 0 COMMENT '是否启用 rerank',
  `status` VARCHAR(32) NOT NULL COMMENT '状态',
  `started_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '结束时间',
  `summary_json` JSON DEFAULT NULL COMMENT '汇总指标',
  `report_path` VARCHAR(512) DEFAULT NULL COMMENT '报告路径',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_id` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 评测任务';

CREATE TABLE IF NOT EXISTS `rag_eval_result` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `run_id` BIGINT NOT NULL COMMENT '评测任务 ID',
  `case_id` BIGINT NOT NULL COMMENT 'Case ID',
  `strategy` VARCHAR(64) NOT NULL COMMENT '检索策略',
  `answer` MEDIUMTEXT COMMENT '生成答案',
  `retrieved_chunks_json` JSON DEFAULT NULL COMMENT '召回结果',
  `recall_at_k` DOUBLE DEFAULT NULL COMMENT 'Recall@K',
  `mrr` DOUBLE DEFAULT NULL COMMENT 'MRR',
  `hit_at_k` DOUBLE DEFAULT NULL COMMENT 'Hit@K',
  `citation_hit` DOUBLE DEFAULT NULL COMMENT '引用命中',
  `refusal_correct` DOUBLE DEFAULT NULL COMMENT '拒答是否正确',
  `latency_ms` BIGINT DEFAULT NULL COMMENT '耗时',
  `error_message` TEXT COMMENT '错误信息',
  PRIMARY KEY (`id`),
  KEY `idx_run_case` (`run_id`, `case_id`),
  KEY `idx_strategy` (`strategy`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 评测结果';
