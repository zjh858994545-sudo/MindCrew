-- MindCrew MCP governance schema

CREATE TABLE IF NOT EXISTS `mcp_client` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `client_id` VARCHAR(80) NOT NULL COMMENT '客户端标识',
  `display_name` VARCHAR(120) NOT NULL COMMENT '客户端展示名',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态: active/disabled',
  `default_rate_limit_per_minute` INT NOT NULL DEFAULT 60 COMMENT '默认每分钟限流',
  `allowed_kb_ids` VARCHAR(500) DEFAULT NULL COMMENT '允许访问的知识库ID列表, 例如 [1,2,3]',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '说明',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_client_id` (`client_id`),
  KEY `idx_mcp_client_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP 客户端策略';

CREATE TABLE IF NOT EXISTS `mcp_tool_policy` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `client_id` VARCHAR(80) NOT NULL COMMENT '客户端标识',
  `tool_name` VARCHAR(100) NOT NULL COMMENT '工具名称',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许调用',
  `rate_limit_per_minute` INT NOT NULL DEFAULT 60 COMMENT '工具级每分钟限流',
  `kb_scope_json` VARCHAR(500) DEFAULT NULL COMMENT '工具级知识库范围, 例如 [1,2]',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '说明',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_policy_client_tool` (`client_id`, `tool_name`),
  KEY `idx_mcp_policy_tool` (`tool_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP 工具调用策略';

CREATE TABLE IF NOT EXISTS `mcp_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `request_id` VARCHAR(80) NOT NULL COMMENT '请求ID',
  `client_id` VARCHAR(80) NOT NULL COMMENT '客户端标识',
  `user_id` VARCHAR(80) DEFAULT NULL COMMENT '用户ID',
  `tool_name` VARCHAR(100) NOT NULL COMMENT '工具名称',
  `action` VARCHAR(32) NOT NULL DEFAULT 'CALL' COMMENT '动作',
  `status` VARCHAR(32) NOT NULL COMMENT '结果: SUCCESS/ERROR/SKIPPED/BLOCK',
  `latency_ms` INT NOT NULL DEFAULT 0 COMMENT '耗时',
  `reason` VARCHAR(255) DEFAULT NULL COMMENT '原因',
  `input_summary` TEXT COMMENT '输入摘要',
  `output_summary` TEXT COMMENT '输出摘要',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_mcp_audit_client_time` (`client_id`, `create_time`),
  KEY `idx_mcp_audit_tool_time` (`tool_name`, `create_time`),
  KEY `idx_mcp_audit_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP 调用审计日志';

INSERT INTO `mcp_client` (`client_id`, `display_name`, `status`, `default_rate_limit_per_minute`, `allowed_kb_ids`, `description`)
VALUES
  ('internal-agent', 'MindCrew Agent', 'active', 120, NULL, 'MindCrew 内部 Agent 工具调用客户端'),
  ('public-mcp', 'Public MCP Client', 'active', 30, NULL, '外部 MCP 客户端默认策略')
ON DUPLICATE KEY UPDATE
  `display_name` = VALUES(`display_name`),
  `status` = VALUES(`status`),
  `default_rate_limit_per_minute` = VALUES(`default_rate_limit_per_minute`),
  `description` = VALUES(`description`);

INSERT INTO `mcp_tool_policy` (`client_id`, `tool_name`, `enabled`, `rate_limit_per_minute`, `kb_scope_json`, `description`)
VALUES
  ('internal-agent', 'doc_search', 1, 120, NULL, '内部 Agent 语义检索'),
  ('internal-agent', 'keyword_search', 1, 120, NULL, '内部 Agent 关键词检索'),
  ('internal-agent', 'web_search', 1, 30, NULL, '内部 Agent 实时联网检索'),
  ('internal-agent', 'recall_memory', 1, 120, NULL, '内部 Agent 记忆召回'),
  ('internal-agent', 'store_memory', 1, 60, NULL, '内部 Agent 记忆写入'),
  ('public-mcp', 'doc_search', 1, 30, NULL, '外部客户端语义检索'),
  ('public-mcp', 'keyword_search', 1, 30, NULL, '外部客户端关键词检索'),
  ('public-mcp', 'web_search', 1, 10, NULL, '外部客户端联网检索'),
  ('public-mcp', 'recall_memory', 1, 30, NULL, '外部客户端记忆召回'),
  ('public-mcp', 'store_memory', 0, 5, NULL, '外部客户端默认禁止写入记忆')
ON DUPLICATE KEY UPDATE
  `enabled` = VALUES(`enabled`),
  `rate_limit_per_minute` = VALUES(`rate_limit_per_minute`),
  `description` = VALUES(`description`);
