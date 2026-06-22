-- MindCrew Agent Trace and Safety Guard schema

CREATE TABLE IF NOT EXISTS `agent_trace` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `trace_id` VARCHAR(80) NOT NULL COMMENT 'Trace ID',
  `conversation_id` BIGINT DEFAULT NULL COMMENT '会话 ID',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '用户 ID',
  `question` TEXT COMMENT '问题摘要',
  `answer` MEDIUMTEXT COMMENT '答案摘要',
  `status` VARCHAR(32) NOT NULL COMMENT '状态',
  `total_latency_ms` BIGINT DEFAULT NULL COMMENT '总耗时',
  `model_name` VARCHAR(128) DEFAULT NULL COMMENT '模型名',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trace_id` (`trace_id`),
  KEY `idx_conversation` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Trace 主表';

CREATE TABLE IF NOT EXISTS `agent_trace_span` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `trace_id` VARCHAR(80) NOT NULL COMMENT 'Trace ID',
  `span_id` VARCHAR(80) NOT NULL COMMENT 'Span ID',
  `parent_span_id` VARCHAR(80) DEFAULT NULL COMMENT '父 Span ID',
  `span_type` VARCHAR(64) NOT NULL COMMENT 'Span 类型',
  `name` VARCHAR(128) NOT NULL COMMENT 'Span 名称',
  `input_summary` TEXT COMMENT '输入摘要',
  `output_summary` TEXT COMMENT '输出摘要',
  `latency_ms` BIGINT DEFAULT NULL COMMENT '耗时',
  `status` VARCHAR(32) NOT NULL COMMENT '状态',
  `error_message` TEXT COMMENT '错误信息',
  `started_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `ended_at` DATETIME DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_trace` (`trace_id`),
  KEY `idx_span_type` (`span_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Trace Span';

CREATE TABLE IF NOT EXISTS `safety_event_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `trace_id` VARCHAR(80) DEFAULT NULL COMMENT 'Trace ID',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '用户 ID',
  `risk_type` VARCHAR(64) NOT NULL COMMENT '风险类型',
  `risk_level` VARCHAR(32) NOT NULL COMMENT '风险等级',
  `action` VARCHAR(32) NOT NULL COMMENT '处理动作',
  `matched_rule` VARCHAR(128) DEFAULT NULL COMMENT '命中规则',
  `input_summary` TEXT COMMENT '输入摘要',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_trace` (`trace_id`),
  KEY `idx_risk` (`risk_type`, `risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Safety Guard 安全事件';
